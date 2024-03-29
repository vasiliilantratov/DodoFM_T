package ru.dodofm_test.dodofm_t.classes;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

import ru.dodofm_test.dodofm_t.Const;
import ru.dodofm_test.dodofm_t.receivers.NoisyAudioStreamReceiver;
import ru.dodofm_test.dodofm_t.services.NotificationService;

import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;


public class Player implements PlayerCallback {

        private final int AUDIO_BUFFER_CAPACITY_MS = 800;
        private final int AUDIO_DECODE_CAPACITY_MS = 400;

        private final String SUFFIX_PLS = ".pls";
        private final String SUFFIX_RAM = ".ram";
        private final String SUFFIX_WAX = ".wax";

        private MultiPlayer mRadioPlayer;

        private AudioManager mAudioManager;

        private NoisyAudioStreamReceiver mNoisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
        private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        private static Player instance = new Player();

        private Context context = null;

        private static boolean mIsReady = true;
        //private static boolean mIsStop = true;

        private static String mUrl = null;

        private int mCurrentVolume;

    private Player() {}

    /*
    public static void newPlayerInstatnce() {
        instance = new Player();
    }
    */

    public static void start(Context context, String URL) {
        instance.mUrl = URL;
        instance.context = context;
        RadioState.state = RadioState.State.LOADING;
        if (!mIsReady) {
            //mIsReady = false;
            instance.getPlayer().stop();
        } else {
            if (instance.checkSuffix(URL)) {
                instance.decodeStreamLink(URL);
            } else {
                //Log.d("PlayerTag", "");
                instance.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                int result = instance.mAudioManager.requestAudioFocus(instance.afChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                Log.d("PlayerTag", "result = " + String.valueOf(result));
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    instance.getPlayer().playAsync(URL);
                } else {
                    RadioState.state = RadioState.State.STOP;
                }
            }
        }
    }

    public static void stop() {
        instance.getPlayer().stop();
        //instance.getPlayer().;
    }

    public static void offRadio() {
        mIsReady = true;
        //instance.mAudioManager.abandonAudioFocus(instance.afChangeListener);
    }

    private boolean checkSuffix(String streamUrl) {
        if (streamUrl.contains(SUFFIX_PLS) ||
                streamUrl.contains(SUFFIX_RAM) ||
                streamUrl.contains(SUFFIX_WAX))
            return true;
        else
            return false;
    }

    private void decodeStreamLink(String streamLink) {
        new StreamLinkDecoder(streamLink) {
            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                start(context, s);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void playerStarted() {
        context.registerReceiver(mNoisyAudioStreamReceiver, intentFilter);
        RadioState.state = RadioState.State.PLAY;
        mIsReady = false;
        RadioState.notifRadioStarted();
    }

    @Override
    public void playerStopped(int i) {
        //mIsStop = true;
        context.unregisterReceiver(mNoisyAudioStreamReceiver);
        mIsReady = true;
        RadioState.notifRadioStopped();
        if ((RadioState.state != RadioState.State.STOP) && ((RadioState.state != RadioState.State.INTERRUPTED) || (RadioState.state == RadioState.State.LOADING))) {
            start(context, mUrl);
        }
    }

    @Override
    public void playerMetadata(String s, String s1) {
        RadioState.notifRadioMetadata(s, s1);
    }

    @Override
    public void playerException(Throwable throwable) {
        RadioState.notifRadioError();
        throwable.printStackTrace();
        RadioState.state = RadioState.State.STOP;
    }

    @Override
    public void playerAudioTrackCreated(AudioTrack audioTrack) {

    }

    @Override
    public void playerPCMFeedBuffer(boolean b, int i, int i1) {

    }

    private MultiPlayer getPlayer() {
        try {
            java.net.URL.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
                public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
                    Log.d("LOG", "Asking for stream handler for protocol: '" + protocol + "'");
                    if ("icy".equals(protocol))
                        return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    return null;
                }
            });
        } catch (Throwable t) {
            Log.w("LOG", "Cannot set the ICY URLStreamHandler - maybe already set ? - " + t);
        }

        if (mRadioPlayer == null) {
            mRadioPlayer = new MultiPlayer(this, AUDIO_BUFFER_CAPACITY_MS, AUDIO_DECODE_CAPACITY_MS);
            mRadioPlayer.setResponseCodeCheckEnabled(false);
            mRadioPlayer.setPlayerCallback(this);

            /*
            TelephonyManager mTelephonyManager = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);
            if (mTelephonyManager != null)
                mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            */
        }
        return mRadioPlayer;
    }

    AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            //Log.d("PlayerTag", "focusChange = " + String.valueOf(focusChange));
            if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT) {
                // Pause playback
                RadioState.state = RadioState.State.INTERRUPTED;
                Intent pauseIntent = new Intent(context, NotificationService.class);
                pauseIntent.setAction(Const.ACTION.PLAY_ACTION);
                PendingIntent ppauseIntent = PendingIntent.getService(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                try {
                    ppauseIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                Log.d("PlayerTag", "AudioFocusChange AUDIOFOCUS_LOSS_TRANSIENT");
            } else if ( focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int newVolume = (int) (0.3 * mCurrentVolume);
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_PLAY_SOUND);
                RadioState.isTransientCanDuck = true;
                Log.d("PlayerTag", "mCurrentVolume = " + String.valueOf(mCurrentVolume));
                Log.d("PlayerTag", "newVolume = " + String.valueOf(newVolume));
                Log.d("PlayerTag", "AudioFocusChange AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Resume playback
                if (RadioState.state == RadioState.State.INTERRUPTED) {
                    Intent pauseIntent = new Intent(context, NotificationService.class);
                    pauseIntent.setAction(Const.ACTION.PLAY_ACTION);
                    PendingIntent ppauseIntent = PendingIntent.getService(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    try {
                        ppauseIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
                if (RadioState.isTransientCanDuck) {
                    RadioState.isTransientCanDuck = false;
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mCurrentVolume, AudioManager.FLAG_PLAY_SOUND);
                }
                Log.d("PlayerTag", "AudioFocusChange AUDIOFOCUS_GAIN");
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                //manager.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
                if (RadioState.state == RadioState.State.PLAY) {
                    Intent pauseIntent = new Intent(context, NotificationService.class);
                    pauseIntent.setAction(Const.ACTION.PLAY_ACTION);
                    PendingIntent ppauseIntent = PendingIntent.getService(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    try {
                        ppauseIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
                mAudioManager.abandonAudioFocus(afChangeListener);
                Log.d("PlayerTag", "AudioFocusChange AUDIOFOCUS_LOSS");
            }
        }
    };
}
