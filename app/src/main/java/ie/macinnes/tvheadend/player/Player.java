/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.tvheadend.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.PlaybackParams;
import android.media.tv.TvContract;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.TvContractUtils;

public class Player implements ExoPlayer.EventListener {
    private static final String TAG = Player.class.getName();

    private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
    private static final int TEXT_UNIT_PIXELS = 0;
    public static final long INVALID_TIMESHIFT_TIME = Subscriber.INVALID_TIMESHIFT_TIME;

    public interface Listener {
        /**
         * Called when ther player state changes.
         *
         * @param playWhenReady Whether playback will proceed when ready.
         * @param playbackState One of the {@code STATE} constants defined in the {@link ExoPlayer}
         *     interface.
         */
        void onPlayerStateChanged(boolean playWhenReady, int playbackState);

        /**
         * Called when an error occurs.
         *
         * @param error The error.
         */
        void onPlayerError(ExoPlaybackException error);

        /**
         * Called when the available or selected tracks change.
         *
         * @param tracks a list of tracks
         */
        void onTracksChanged(List<TvTrackInfo> tracks, SparseArray<String> selectedTracks);
    }

    private final Context mContext;
    private final SimpleHtspConnection mConnection;
    private final Listener mListener;

    private final SharedPreferences mSharedPreferences;

    private SimpleExoPlayer mExoPlayer;
    private TvheadendTrackSelector mTrackSelector;
    private EventLogger mEventLogger;
    private HtspDataSource.HtspFactory mDataSourceHtspFactory;
    private ExtractorsFactory mExtractorsFactory;

    private View mOverlayView;
    private DebugTextViewHelper mDebugViewHelper;
    private SubtitleView mSubtitleView;
    private LinearLayout mRadioInfoView;

    private MediaSource mMediaSource;
    private Subscriber mSubscriber;

    private Uri mCurrentChannelUri;

    public Player(Context context, SimpleHtspConnection connection, Listener listener) {
        mContext = context;
        mConnection = connection;
        mListener = listener;

        mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        buildExoPlayer();
    }

    public void open(Uri channelUri) {
        // Stop any existing playback
        stop();

        mCurrentChannelUri = channelUri;

        // Create the media source
        buildHtspMediaSource(channelUri);

        // Prepare the media source
        mExoPlayer.prepare(mMediaSource);
    }

    public void release() {
        // Stop any existing playback
        stop();

        if (mDebugViewHelper != null) {
            mDebugViewHelper.stop();
            mDebugViewHelper = null;
        }

        mSubtitleView = null;
        mOverlayView = null;

        // Release ExoPlayer
        mExoPlayer.removeListener(this);
        mExoPlayer.release();
    }

    public void setSurface(Surface surface) {
        mExoPlayer.setVideoSurface(surface);
    }

    public void setVolume(float volume) {
        mExoPlayer.setVolume(volume);
    }

    public boolean selectTrack(int type, String trackId) {
        return mTrackSelector.selectTrack(type, trackId);
    }

    public void play() {
        // Start playback when ready
        mExoPlayer.setPlayWhenReady(true);
    }

    public void resume() {
        if (mSubscriber != null) {
            Log.d(TAG, "Resuming Subscriber");

            mExoPlayer.setPlayWhenReady(true);
            mSubscriber.resume();
        } else {
            Log.w(TAG, "Unable to resume, no Subscriber available");
        }
    }

    public void pause() {
        if (mSubscriber != null) {
            Log.d(TAG, "Pausing Subscriber");

            mExoPlayer.setPlayWhenReady(false);
            mSubscriber.pause();
        } else {
            Log.w(TAG, "Unable to pause, no Subscriber available");
        }
    }

    //TODO: Never used, check if this is still needed
    public void seek(long timeMs) {
        if (mSubscriber != null) {
            Log.d(TAG, "Seeking to time: " + timeMs);
            long seekPts = (timeMs * 1000) - mSubscriber.getTimeshiftStartTime();
            seekPts = Math.max(seekPts, mSubscriber.getTimeshiftStartPts());

            mExoPlayer.seekTo(seekPts / 1000);
        } else {
            Log.w(TAG, "Unable to seek, no Subscriber available");
        }
    }

    //TODO: Never used, check if this is still needed
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setPlaybackParams(PlaybackParams params) {
        float rawSpeed = params.getSpeed();
        int speed = (int) rawSpeed;
        int translatedSpeed;

        switch(speed) {
            case 0:
                translatedSpeed = 100;
                break;
            case -2:
                translatedSpeed = -200;
                break;
            case -4:
                translatedSpeed = -300;
                break;
            case -12:
                translatedSpeed = -400;
                break;
            case -48:
                translatedSpeed = -500;
                break;
            case 2:
                translatedSpeed = 200;
                break;
            case 4:
                translatedSpeed = 300;
                break;
            case 12:
                translatedSpeed = 400;
                break;
            case 48:
                translatedSpeed = 500;
                break;
            default:
                Log.d(TAG, "Unknown speed??? " + rawSpeed);
            return;
        }

        Log.d(TAG, "Speed: " + params.getSpeed() + " / " + translatedSpeed);

        if (mSubscriber != null) {
            mSubscriber.setSpeed(translatedSpeed);
            mExoPlayer.setPlaybackParameters(new PlaybackParameters(translatedSpeed, 0));
        }
    }

    public void stop() {
        mExoPlayer.stop();
        mTrackSelector.clearSelectionOverrides();
        mDataSourceHtspFactory.releaseCurrentDataSource();

        if (mMediaSource != null) {
            mMediaSource.releaseSource();
        }
    }
    public long getTimeshiftStartPosition() {
        if (mSubscriber != null) {
            long startTime = mSubscriber.getTimeshiftStartTime();
            if (startTime != Subscriber.INVALID_TIMESHIFT_TIME) {
                return startTime / 1000;
            }
        } else {
            Log.w(TAG, "Unable to getTimeshiftStartPosition, no Subscriber available");
        }

        return INVALID_TIMESHIFT_TIME;
    }

    public long getTimeshiftCurrentPosition() {
        if (mSubscriber != null) {
            long offset = mSubscriber.getTimeshiftOffsetPts();
            if (offset != Subscriber.INVALID_TIMESHIFT_TIME) {
                return System.currentTimeMillis() + (offset / 1000);
            }
        } else {
            Log.w(TAG, "Unable to getTimeshiftCurrentPosition, no Subscriber available");
        }

        return INVALID_TIMESHIFT_TIME;
    }

    public View getOverlayView(CaptioningManager.CaptionStyle captionStyle, float fontScale) {
        if (mOverlayView == null) {
            LayoutInflater lI = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            mOverlayView = lI.inflate(R.layout.player_overlay_view, null);
        }

        if (mDebugViewHelper == null) {
            mDebugViewHelper = getDebugTextView();

            if (mDebugViewHelper != null) {
                mDebugViewHelper.start();
            }
        }

        if (mSubtitleView == null) {
            mSubtitleView = getSubtitleView(captionStyle, fontScale);
            mExoPlayer.setTextOutput(mSubtitleView);
        }

        if (mRadioInfoView == null) {
            mRadioInfoView = (LinearLayout) mOverlayView.findViewById(R.id.radio_info_view);
        }

        if (mRadioInfoView == null) {
            mRadioInfoView = (LinearLayout) mOverlayView.findViewById(R.id.radio_info_view);
        }

        if (mRadioInfoView == null) {
            mRadioInfoView = (LinearLayout) mOverlayView.findViewById(R.id.radio_info_view);
        }

        return mOverlayView;
    }

    private DebugTextViewHelper getDebugTextView() {
        final boolean enableDebugTextView = mSharedPreferences.getBoolean(
                Constants.KEY_DEBUG_TEXT_VIEW_ENABLED,
                mContext.getResources().getBoolean(R.bool.pref_default_debug_text_view_enabled)
        );

        if (enableDebugTextView) {
            TextView textView = (TextView) mOverlayView.findViewById(R.id.debug_text_view);
            textView.setVisibility(View.VISIBLE);
            return new DebugTextViewHelper(
                    mExoPlayer, textView);
        } else {
            return null;
        }
    }

    private SubtitleView getSubtitleView(CaptioningManager.CaptionStyle captionStyle, float fontScale) {
        SubtitleView view = (SubtitleView) mOverlayView.findViewById(R.id.subtitle_view);

        CaptionStyleCompat captionStyleCompat = CaptionStyleCompat.createFromCaptionStyle(captionStyle);

        float captionTextSize = getCaptionFontSize();
        captionTextSize *= fontScale;

        final boolean applyEmbeddedStyles = mSharedPreferences.getBoolean(
                Constants.KEY_CAPTIONS_APPLY_EMBEDDED_STYLES,
                mContext.getResources().getBoolean(R.bool.pref_default_captions_apply_embedded_styles)
        );

        view.setStyle(captionStyleCompat);
        view.setVisibility(View.VISIBLE);
        view.setFixedTextSize(TEXT_UNIT_PIXELS, captionTextSize);
        view.setApplyEmbeddedStyles(applyEmbeddedStyles);

        return view;
    }

    // Misc Internal Methods
    private void buildExoPlayer() {
        RenderersFactory mRenderersFactory = new TvheadendRenderersFactory(mContext);
        mTrackSelector = buildTrackSelector();
        LoadControl mLoadControl = buildLoadControl();

        mExoPlayer = ExoPlayerFactory.newSimpleInstance(mRenderersFactory, mTrackSelector, mLoadControl);
        mExoPlayer.addListener(this);

        // Add the EventLogger
        mEventLogger = new EventLogger(mTrackSelector);
        mExoPlayer.addListener(mEventLogger);
        mExoPlayer.setAudioDebugListener(mEventLogger);
        mExoPlayer.setVideoDebugListener(mEventLogger);

        final String streamProfile = mSharedPreferences.getString(
                Constants.KEY_HTSP_STREAM_PROFILE,
                mContext.getResources().getString(R.string.pref_default_htsp_stream_profile)
        );

        // Produces DataSource instances through which media data is loaded.
        mDataSourceHtspFactory = new HtspDataSource.HtspFactory(mContext, mConnection, streamProfile);

        // Produces Extractor instances for parsing the media data.
<<<<<<< HEAD
        mExtractorsFactory = new HtspExtractor.Factory(mContext);
=======
        mExtractorsFactory = new TvheadendExtractorsFactory(mContext);
>>>>>>> upstream-develop
    }

    private TvheadendTrackSelector buildTrackSelector() {
        TrackSelection.Factory trackSelectionFactory =
                new AdaptiveTrackSelection.Factory(null);

        TvheadendTrackSelector trackSelector = new TvheadendTrackSelector(trackSelectionFactory);

        final boolean enableAudioTunneling = mSharedPreferences.getBoolean(
            Constants.KEY_AUDIO_TUNNELING_ENABLED,
            mContext.getResources().getBoolean(R.bool.pref_default_audio_tunneling_enabled)
        );

        if (enableAudioTunneling) {
            trackSelector.setTunnelingAudioSessionId(C.generateAudioSessionIdV21(mContext));
        }
<<<<<<< HEAD

        String languageCode = Locale.getDefault().getISO3Language();

        DefaultTrackSelector.Parameters trackSelectorParameters = new DefaultTrackSelector.Parameters()
                .withPreferredAudioLanguage(languageCode);

        trackSelector.setParameters(trackSelectorParameters);
=======
>>>>>>> upstream-develop

        return trackSelector;
    }

    private LoadControl buildLoadControl() {
        int bufferForPlaybackMs = Integer.parseInt(
                mSharedPreferences.getString(
                        Constants.KEY_BUFFER_PLAYBACK_MS,
                        mContext.getResources().getString(R.string.pref_default_buffer_playback_ms)
                )
        );

        return new DefaultLoadControl(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                bufferForPlaybackMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        );
    }

    private void buildHtspMediaSource(Uri channelUri) {
        // This is the MediaSource representing the media to be played.
        mMediaSource = new ExtractorMediaSource(channelUri,
                mDataSourceHtspFactory, mExtractorsFactory, null, mEventLogger);
    }

    private float getCaptionFontSize() {
        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        return Math.max(mContext.getResources().getDimension(R.dimen.subtitle_minimum_font_size),
                CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
    }

    private boolean getTrackStatusBoolean(TrackSelection selection, TrackGroup group,
                                          int trackIndex) {
        return selection != null && selection.getTrackGroup() == group
                && selection.indexOf(trackIndex) != C.INDEX_UNSET;
    }

    // ExoPlayer.EventListener Methods
    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        // Don't care about this event here
    }

    //TODO: This should probably be simplified
    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mTrackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) {
            return;
        }

        // Process Tracks
        List<TvTrackInfo> tracks = new ArrayList<>();
        SparseArray<String> selectedTracks = new SparseArray<>();

        // Keep track of weather we have a video track available
        boolean hasVideoTrack = false;

        for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.length; rendererIndex++) {
            TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            TrackSelection trackSelection = trackSelections.get(rendererIndex);
            if (rendererTrackGroups.length > 0) {
                for (int groupIndex = 0; groupIndex < rendererTrackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = rendererTrackGroups.get(groupIndex);
                    for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                        int formatSupport = mappedTrackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex);

                        if (formatSupport == RendererCapabilities.FORMAT_HANDLED) {
                            Format format = trackGroup.getFormat(trackIndex);
                            TvTrackInfo tvTrackInfo = ExoPlayerUtils.buildTvTrackInfo(format);

                            if (tvTrackInfo != null) {
                                tracks.add(tvTrackInfo);

                                Boolean selected = getTrackStatusBoolean(trackSelection, trackGroup, trackIndex);

                                if (selected) {
                                    int trackType = MimeTypes.getTrackType(format.sampleMimeType);

                                    //TODO: Default case
                                    switch (trackType) {
                                        case C.TRACK_TYPE_VIDEO:
                                            hasVideoTrack = true;
                                            selectedTracks.put(TvTrackInfo.TYPE_VIDEO, format.id);
                                            break;
                                        case C.TRACK_TYPE_AUDIO:
                                            selectedTracks.put(TvTrackInfo.TYPE_AUDIO, format.id);
                                            break;
                                        case C.TRACK_TYPE_TEXT:
                                            selectedTracks.put(TvTrackInfo.TYPE_SUBTITLE, format.id);
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if(hasVideoTrack) {
            disableRadioInfoScreen();
        } else {
            enableRadioInfoScreen();
        }

        mListener.onTracksChanged(tracks, selectedTracks);
    }

    private void enableRadioInfoScreen() {
        // No video track available, use the channel logo as a substitute
        Log.i(TAG, "No video track available");

        try {
            String channelName = TvContractUtils.getChannelName(mContext, Integer.parseInt(mCurrentChannelUri.getPath().substring(1)));
            TextView radioChannelName = (TextView) mRadioInfoView.findViewById(R.id.radio_channel_name);
            radioChannelName.setText(channelName);

            ImageView radioChannelIcon = (ImageView) mRadioInfoView.findViewById(R.id.radio_channel_icon);

            long androidChannelId = TvContractUtils.getChannelId(mContext, Integer.parseInt(mCurrentChannelUri.getPath().substring(1)));
            Uri channelIconUri = TvContract.buildChannelLogoUri(androidChannelId);

            InputStream is = mContext.getContentResolver().openInputStream(channelIconUri);

            BitmapDrawable iconImage = new BitmapDrawable(mContext.getResources(), is);
            radioChannelIcon.setImageDrawable(iconImage);

            mRadioInfoView.setVisibility(View.VISIBLE);
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch logo", e);
        }
    }

    private void disableRadioInfoScreen() {
        Log.d(TAG, "Video track is available");
        mRadioInfoView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (isLoading) {
            // Fetch the Subscriber for later use
            HtspDataSource dataSource = mDataSourceHtspFactory.getCurrentDataSource();

            if (dataSource != null) {
                // TODO: Hold a WeakReference to the Subscriber instead...
                mSubscriber = dataSource.getSubscriber();
            }
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        mListener.onPlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        mListener.onPlayerError(error);
    }

    @Override
    public void onPositionDiscontinuity() {
        // Don't care about this event here
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Don't care about this event here
    }
}
