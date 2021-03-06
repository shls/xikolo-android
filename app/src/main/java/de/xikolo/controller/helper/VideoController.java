package de.xikolo.controller.helper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.xikolo.GlobalApplication;
import de.xikolo.R;
import de.xikolo.data.entities.Course;
import de.xikolo.data.entities.Item;
import de.xikolo.data.entities.Module;
import de.xikolo.data.entities.VideoItemDetail;
import de.xikolo.data.preferences.AppPreferences;
import de.xikolo.model.DownloadModel;
import de.xikolo.util.Config;
import de.xikolo.util.NetworkUtil;
import de.xikolo.util.PlaybackSpeed;
import de.xikolo.util.ToastUtil;
import de.xikolo.view.CustomFontTextView;
import de.xikolo.view.CustomSizeVideoView;

public class VideoController {

    public static final String TAG = VideoController.class.getSimpleName();

    private static final int MILLISECONDS = 100;

    private static final int DEFAULT_TIMEOUT = 3000;
    private static final int FADE_OUT = 1;

    private static final int PLAYBACK_PARAMS_SDK_LEVEL = 23;

    private DownloadModel downloadModel;

    private Activity activity;

    private CustomSizeVideoView videoView;

    private View videoContainer;

    private View videoProgress;

    private View videoController;

    private CustomFontTextView buttonPlay;
    private TextView buttonRetry;

    private SeekBar seekBar;
    private TextView textCurrentTime;
    private TextView textTotalTime;
    private CustomFontTextView textHdSwitch;
    private TextView textPlaybackSpeed;
    private View viewOfflineHint;

    private View viewVideoWarning;
    private TextView textVideoWarning;

    private ControllerListener controllerListener;

    private Runnable seekBarUpdater;

    private Handler handler = new MessageHandler(this);

    private boolean seekBarUpdaterIsRunning = false;

    private boolean userIsSeeking = false;

    private boolean isPlaying = true;

    private PlaybackSpeed currentPlaybackSpeed = PlaybackSpeed.x10;

    private Course course;
    private Module module;
    private Item<VideoItemDetail> videoItemDetails;

    private MediaPlayer mediaPlayer;

    private enum VideoMode {
        SD, HD
    }

    private VideoMode videoMode;

    public VideoController(Activity activity, View videoContainer) {
        this.activity = activity;

        this.videoContainer = videoContainer;
        videoView = (CustomSizeVideoView) this.videoContainer.findViewById(R.id.videoView);
        videoController = this.videoContainer.findViewById(R.id.videoController);

        videoProgress = this.videoContainer.findViewById(R.id.videoProgress);

        seekBar = (SeekBar) this.videoContainer.findViewById(R.id.videoSeekBar);
        textCurrentTime = (TextView) videoController.findViewById(R.id.currentTime);
        textTotalTime = (TextView) videoController.findViewById(R.id.totalTime);
        textHdSwitch = (CustomFontTextView) videoController.findViewById(R.id.hdSwitch);
        textPlaybackSpeed = (TextView) videoController.findViewById(R.id.playbackSpeed);

        buttonPlay = (CustomFontTextView) this.videoContainer.findViewById(R.id.btnPlay);

        viewOfflineHint = this.videoContainer.findViewById(R.id.offlineHint);

        viewVideoWarning = this.videoContainer.findViewById(R.id.videoWarning);
        textVideoWarning = (TextView) this.videoContainer.findViewById(R.id.videoWarningText);
        buttonRetry = (TextView) this.videoContainer.findViewById(R.id.btnRetry);

        this.videoContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                show();
                return false;
            }
        });

        downloadModel = new DownloadModel(GlobalApplication.getInstance().getJobManager(), activity);

        setupView();
    }

    private void setupView() {
        videoView.setKeepScreenOn(true);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mediaPlayer = mp;

                videoProgress.setVisibility(View.GONE);
                viewVideoWarning.setVisibility(View.GONE);
                seekBar.setMax(videoView.getDuration());
                show();

                textTotalTime.setText(getTimeString(videoView.getDuration()));
                textCurrentTime.setText(getTimeString(0));

                seekTo(videoItemDetails.detail.progress);
                if (isPlaying) {
                    start();
                }

                mp.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                    @Override
                    public void onBufferingUpdate(MediaPlayer mp, int percent) {
                        seekBar.setSecondaryProgress((int) (seekBar.getMax() * (percent / 100.)));
                    }
                });

                if (Build.VERSION.SDK_INT >= PLAYBACK_PARAMS_SDK_LEVEL) {
                    AppPreferences appPreferences = GlobalApplication.getInstance().getPreferencesFactory().getAppPreferences();
                    currentPlaybackSpeed = appPreferences.getVideoPlaybackSpeed();
                    setPlaybackSpeed(currentPlaybackSpeed);
                }

                new Thread(seekBarUpdater).start();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                pause();
                buttonPlay.setText(activity.getString(R.string.icon_reload));
                show();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                        Log.e(TAG, "MediaPlayer.MEDIA_ERROR_UNKNOWN appeared (" + MediaPlayer.MEDIA_ERROR_UNKNOWN + ")");
                        break;
                    case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                        Log.e(TAG, "MediaPlayer.MEDIA_ERROR_SERVER_DIED appeared (" + MediaPlayer.MEDIA_ERROR_SERVER_DIED + ")");
                        break;
                }
                if (Build.VERSION.SDK_INT >= 17) {
                    switch (extra) {
                        case MediaPlayer.MEDIA_ERROR_IO:
                            Log.e(TAG, "MediaPlayer.MEDIA_ERROR_IO appeared (" + MediaPlayer.MEDIA_ERROR_IO + ")");
                            break;
                        case MediaPlayer.MEDIA_ERROR_MALFORMED:
                            Log.e(TAG, "MediaPlayer.MEDIA_ERROR_MALFORMED appeared (" + MediaPlayer.MEDIA_ERROR_MALFORMED + ")");
                            break;
                        case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                            Log.e(TAG, "MediaPlayer.MEDIA_ERROR_UNSUPPORTED appeared (" + MediaPlayer.MEDIA_ERROR_UNSUPPORTED + ")");
                            break;
                        case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                            Log.e(TAG, "MediaPlayer.MEDIA_ERROR_TIMED_OUT appeared (" + MediaPlayer.MEDIA_ERROR_TIMED_OUT + ")");
                            break;
                    }
                }

                saveCurrentPosition(mp.getCurrentPosition());
                if (extra == MediaPlayer.MEDIA_ERROR_IO) {
                    videoProgress.setVisibility(View.VISIBLE);
                    ToastUtil.show(R.string.trying_reconnect);
                    updateVideo(course, module, videoItemDetails);
                } else {
                    viewVideoWarning.setVisibility(View.VISIBLE);
                    textVideoWarning.setText(activity.getString(R.string.error_plain));
                }

                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= 17) {
            videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    switch (what) {
                        case MediaPlayer.MEDIA_INFO_UNKNOWN:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_UNKNOWN notified (" + MediaPlayer.MEDIA_INFO_UNKNOWN + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING notified (" + MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            if (Build.VERSION.SDK_INT >= 17) {
                                Log.i(TAG, "MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START notified (" + MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START + ")");
                            }
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_BUFFERING_START notified (" + MediaPlayer.MEDIA_INFO_BUFFERING_START + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_BUFFERING_END notified (" + MediaPlayer.MEDIA_INFO_BUFFERING_END + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING notified (" + MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_NOT_SEEKABLE notified (" + MediaPlayer.MEDIA_INFO_NOT_SEEKABLE + ")");
                            break;
                        case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.i(TAG, "MediaPlayer.MEDIA_INFO_METADATA_UPDATE notified (" + MediaPlayer.MEDIA_INFO_METADATA_UPDATE + ")");
                            break;
                    }
                    if (extra != 0) {
                        Log.i(TAG, "MediaPlayer Info Extra " + extra + " notified");
                    }

                    return true;
                }
            });
        }

        seekBarUpdater = new Runnable() {
            @Override
            public void run() {
                seekBarUpdaterIsRunning = true;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!userIsSeeking) {
                            seekBar.setProgress(videoView.getCurrentPosition());
                            textCurrentTime.setText(getTimeString(videoView.getCurrentPosition()));
                        }
                    }
                });
                if (videoView.getCurrentPosition() < videoView.getDuration()) {
                    seekBar.postDelayed(this, MILLISECONDS);
                } else {
                    seekBarUpdaterIsRunning = false;
                }
            }
        };

        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show();
                if (videoView.isPlaying()) {
                    pause();
                } else {
                    start();
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int progress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    show();
                    this.progress = progress;
                    textCurrentTime.setText(getTimeString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
                seekTo(progress);
            }
        });

        textHdSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHdButton();
            }
        });

        if (Build.VERSION.SDK_INT >= PLAYBACK_PARAMS_SDK_LEVEL) {
            textPlaybackSpeed.setVisibility(View.VISIBLE);
            textPlaybackSpeed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePlaybackSpeed();
                }
            });
        } else {
            textPlaybackSpeed.setVisibility(View.GONE);
            textPlaybackSpeed.setClickable(false);
        }

        buttonRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVideo(course, module, videoItemDetails);
            }
        });
    }

    public void start() {
        buttonPlay.setText(activity.getString(R.string.icon_pause));
        videoView.start();
        isPlaying = true;
        if (!seekBarUpdaterIsRunning) {
            new Thread(seekBarUpdater).start();
        }
    }

    public void pause() {
        buttonPlay.setText(activity.getString(R.string.icon_play));
        videoView.pause();
        isPlaying = false;
        saveCurrentPosition();
    }

    public void seekTo(int progress) {
        videoView.seekTo(progress);
        textCurrentTime.setText(getTimeString(progress));
        seekBar.setProgress(progress);
        if (!seekBarUpdaterIsRunning) {
            new Thread(seekBarUpdater).start();
        }
    }

    @TargetApi(23)
    public void togglePlaybackSpeed() {
        if (mediaPlayer != null) {
            switch (currentPlaybackSpeed) {
                case x07:
                    setPlaybackSpeed(PlaybackSpeed.x10);
                    break;
                case x10:
                    setPlaybackSpeed(PlaybackSpeed.x13);
                    break;
                case x13:
                    setPlaybackSpeed(PlaybackSpeed.x15);
                    break;
                case x15:
                    setPlaybackSpeed(PlaybackSpeed.x18);
                    break;
                case x18:
                    setPlaybackSpeed(PlaybackSpeed.x20);
                    break;
                case x20:
                    setPlaybackSpeed(PlaybackSpeed.x07);
                    break;
            }
        }
    }

    @TargetApi(23)
    public void setPlaybackSpeed(PlaybackSpeed speed) {
        if (mediaPlayer != null) {
            currentPlaybackSpeed = speed;
            textPlaybackSpeed.setText(speed.toString());
            PlaybackParams pp = new PlaybackParams();
            pp.setSpeed(speed.getSpeed());
            mediaPlayer.setPlaybackParams(pp);
            if (!isPlaying) {
                pause();
            }
        }
    }

    public void show() {
        show(DEFAULT_TIMEOUT);
    }

    public void show(int timeout) {
        videoController.setVisibility(View.VISIBLE);
        if (controllerListener != null) {
            controllerListener.onControllerShow();
        }
        Message msg = handler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            handler.removeMessages(FADE_OUT);
            handler.sendMessageDelayed(msg, timeout);
        }
    }

    public void hide() {
        if (videoView.isPlaying()) {
            videoController.setVisibility(View.GONE);
            if (controllerListener != null) {
                controllerListener.onControllerHide();
            }
        } else {
            show();
        }
    }

    private void toggleHdButton() {
        if (videoMode == VideoMode.HD) {
            videoMode = VideoMode.SD;
        } else {
            videoMode = VideoMode.HD;
        }

        saveCurrentPosition();
        updateVideo(course, module, videoItemDetails);
    }

    public void setupVideo(Course course, Module module, Item<VideoItemDetail> video) {
        this.course = course;
        this.module = module;
        videoItemDetails = video;

        int connectivityStatus = NetworkUtil.getConnectivityStatus(activity);
        AppPreferences preferences = GlobalApplication.getInstance().getPreferencesFactory().getAppPreferences();

        if (connectivityStatus == NetworkUtil.TYPE_MOBILE && preferences.isVideoQualityLimitedOnMobile()) {
            videoMode = VideoMode.SD;
        } else {
            videoMode = VideoMode.HD;
        }

        updateVideo(course, module, videoItemDetails);
    }

    private void updateVideo(Course course, Module module, Item<VideoItemDetail> video) {
        String stream;
        DownloadModel.DownloadFileType fileType;

        viewVideoWarning.setVisibility(View.GONE);

        if (videoMode == VideoMode.HD) {
            stream = video.detail.stream.hd_url;
            fileType = DownloadModel.DownloadFileType.VIDEO_HD;
        } else {
            stream = video.detail.stream.sd_url;
            fileType = DownloadModel.DownloadFileType.VIDEO_SD;
        }

        viewOfflineHint.setVisibility(View.GONE);

        if (!downloadModel.downloadRunning(fileType, course, module, video)
                && downloadModel.downloadExists(fileType, course, module, video)) {
            setVideoURI("file://" + downloadModel.getDownloadFile(fileType, course, module, video).getAbsolutePath());
            viewOfflineHint.setVisibility(View.VISIBLE);
        } else if (NetworkUtil.isOnline(activity)) {
            setVideoURI(stream);
        } else if (videoMode == VideoMode.HD) {
            videoMode = VideoMode.SD;
            updateVideo(course, module, video);
        } else {
            viewVideoWarning.setVisibility(View.VISIBLE);
            textVideoWarning.setText(activity.getString(R.string.video_notification_no_offline_video));
        }

        updateHdSwitchColor();
    }

    private void setVideoURI(String uri) {
        if (Config.DEBUG) {
            Log.i(TAG, "Video URI: " + uri);
        }
        videoView.setVideoURI(Uri.parse(uri));
    }

    private void updateHdSwitchColor() {
        if (videoMode == VideoMode.HD) {
            textHdSwitch.setTextColor(ContextCompat.getColor(activity, R.color.video_hd_enabled));
        } else {
            textHdSwitch.setTextColor(ContextCompat.getColor(activity, R.color.video_hd_disabled));
        }
    }

    public void saveCurrentPosition() {
        if (videoView != null && videoItemDetails != null) {
            videoItemDetails.detail.progress = videoView.getCurrentPosition();
        }
    }

    public void saveCurrentPosition(int position) {
        if (videoItemDetails != null) {
            videoItemDetails.detail.progress = position;
        }
    }

    public View getControllerView() {
        return videoController;
    }

    public View getVideoContainer() {
        return videoContainer;
    }

    public VideoItemDetail getVideoItemDetail() {
        if (videoItemDetails != null) {
            return videoItemDetails.detail;
        }
        return null;
    }

    private String getTimeString(int millis) {
        return String.format(Locale.US, "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    public void setControllerListener(ControllerListener listener) {
        this.controllerListener = listener;
    }

    public interface ControllerListener {

        void onControllerShow();

        void onControllerHide();

    }

    private static class MessageHandler extends Handler {
        VideoController mController;

        public MessageHandler(VideoController controller) {
            mController = controller;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FADE_OUT:
                    mController.hide();
                    break;
            }
        }
    }

}
