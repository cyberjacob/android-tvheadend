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
import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.Closeable;
import java.lang.ref.WeakReference;

import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;

    public static class HtspFactory implements DataSource.Factory {
        private static final String TAG = HtspFactory.class.getName();

    public static abstract class Factory implements DataSource.Factory {

        private static final String TAG = HtspDataSource.Factory.class.getName();

        public HtspFactory(Context context, SimpleHtspConnection connection, String streamProfile) {
            mContext = context;
            mConnection = connection;
            mStreamProfile = streamProfile;
        }

        @Override
        public HtspDataSource createDataSource() {
            releaseCurrentDataSource();

            mCurrentDataSource = new WeakReference<>(createDataSourceInternal());
            return mCurrentDataSource.get();
        }

        public HtspDataSource getCurrentDataSource() {
            if (mCurrentDataSource != null) {
                return mCurrentDataSource.get();
            }

            return null;
        }

        public void releaseCurrentDataSource() {
            if (mCurrentDataSource != null) {
                mCurrentDataSource.get().release();
                mCurrentDataSource.clear();
                mCurrentDataSource = null;
            }
        }

    private int mTimeshiftPeriod = 0;

    private final int mDataSourceNumber;
    private Subscriber mSubscriber;

    private DataSpec mDataSpec;

    private ByteBuffer mBuffer;
    private ReentrantLock mLock = new ReentrantLock();

    protected final Context mContext;
    protected SimpleHtspConnection mConnection;
    protected DataSpec mDataSpec;

    public HtspDataSource(Context context, SimpleHtspConnection connection) {
        mContext = context;
        mConnection = connection;
        mStreamProfile = streamProfile;

        SharedPreferences mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        boolean timeshiftEnabled = mSharedPreferences.getBoolean(
                Constants.KEY_TIMESHIFT_ENABLED,
                mContext.getResources().getBoolean(R.bool.pref_default_timeshift_enabled));

        if (timeshiftEnabled) {
            // TODO: Eventually, this should be a preference.
            mTimeshiftPeriod = 3600;
        }

        mDataSourceNumber = sDataSourceCount.incrementAndGet();

        Log.d(TAG, "New HtspDataSource instantiated ("+mDataSourceNumber+")");

        try {
            mBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            mBuffer.limit(0);
        } catch (OutOfMemoryError e) {
            // Since we're allocating a large buffer here, it's fairly safe to assume we'll have
            // enough memory to catch and throw this exception. We do this, as each OOM exception
            // message is unique (lots of #'s of bytes available/used/etc) and means crash reporting
            // doesn't group things nicely.
            throw new HtspOutOfMemoryError("OutOfMemoryError when allocating HtspDataSource buffer ("+mDataSourceNumber+")", e);
        }

        mSubscriber = new Subscriber(mConnection);
        mSubscriber.addSubscriptionListener(this);
        mConnection.addAuthenticationListener(mSubscriber);
    }

    public Subscriber getSubscriber() {
        return mSubscriber;
    }

    public void release() {
        if (mConnection != null) {
            mConnection.removeAuthenticationListener(mSubscriber);
            mConnection = null;
        }

        if (mSubscriber != null) {
            mSubscriber.removeSubscriptionListener(this);
            mSubscriber.unsubscribe();
            mSubscriber = null;
        }

        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    public abstract void release();

    // Methods used by the player, which need to be passed to the Subscriber
    public abstract void pause();
    public abstract void resume();
//    public abstract void seek(long timeMs);
    public abstract long getTimeshiftStartTime();
    public abstract long getTimeshiftStartPts();
    public abstract long getTimeshiftOffsetPts();
    public abstract void setSpeed(int speed);

    // DataSource Methods
    @Override
<<<<<<< HEAD
    public long open(DataSpec dataSpec) throws IOException {
        Log.i(TAG, "Opening HTSP DataSource ("+mDataSourceNumber+")");
        mDataSpec = dataSpec;

        if (!mIsSubscribed) {
            try {
                mSubscriber.subscribe(Long.parseLong(
                        dataSpec.uri.getHost()), mStreamProfile, mTimeshiftPeriod);
                mIsSubscribed = true;
            } catch (HtspNotConnectedException e) {
                throw new IOException("Failed to open DataSource, HTSP not connected (" + mDataSourceNumber + ")", e);
            }
        }

        long seekPosition = mDataSpec.position;
        if (seekPosition > 0 && mTimeshiftPeriod > 0) {
            Log.d(TAG, "seek to time PTS: " + seekPosition);

            mSubscriber.skip(seekPosition);
            mBuffer.clear();
            mBuffer.limit(0);
        }

        mIsOpen = true;

        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }

        // If the buffer is empty, block until we have at least 1 byte
        while (mIsOpen && mBuffer.remaining() == 0) {
            try {
                if (Constants.DEBUG)
                    Log.v(TAG, "Blocking for more data ("+mDataSourceNumber+")");
                Thread.sleep(250);
            } catch (InterruptedException e) {
                //TODO: Clean up and re-throw, or java will do it anway
                // Ignore.
                Log.w(TAG, "Caught InterruptedException ("+mDataSourceNumber+")");
                return 0;
            }
        }

        if (!mIsOpen && mBuffer.remaining() == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int length;

        mLock.lock();
        try {
            int remaining = mBuffer.remaining();
            length = remaining >= readLength ? readLength : remaining;

            mBuffer.get(buffer, offset, length);
            mBuffer.compact();
            mBuffer.flip();
        } finally {
            mLock.unlock();
        }

        return length;
    }

    @Override
    public Uri getUri() {
        if (mDataSpec != null) {
            return mDataSpec.uri;
        }

        return null;
    }

    @Override
    public void close() throws IOException {
        Log.i(TAG, "Closing HTSP DataSource ("+mDataSourceNumber+")");
        mIsOpen = false;
    }

    // Subscription.Listener Methods
    @Override
    public void onSubscriptionStart(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStart ("+mDataSourceNumber+")");
        serializeMessageToBuffer(message);
    }

    @Override
    public void onSubscriptionStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionSkip(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionSpeed(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionStop(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStop ("+mDataSourceNumber+")");
        mIsOpen = false;
    }

    @Override
    public void onQueueStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSignalStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onTimeshiftStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onMuxpkt(@NonNull HtspMessage message) {
        serializeMessageToBuffer(message);
    }

    // Misc Internal Methods
    private void serializeMessageToBuffer(@NonNull HtspMessage message) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        mLock.lock();
        try (
                ObjectOutputStream objectOutput = new ObjectOutputStream(outputStream)
        ) {
            objectOutput.writeUnshared(message);
            objectOutput.flush();

            mBuffer.position(mBuffer.limit());
            mBuffer.limit(mBuffer.capacity());

            mBuffer.put(outputStream.toByteArray());

            mBuffer.flip();
        } catch (IOException e) {
            // Ignore?
            Log.w(TAG, "Caught IOException, ignoring ("+mDataSourceNumber+")", e);
        } finally {
            mLock.unlock();
            try {
                outputStream.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    private class HtspOutOfMemoryError extends RuntimeException {
        HtspOutOfMemoryError(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
