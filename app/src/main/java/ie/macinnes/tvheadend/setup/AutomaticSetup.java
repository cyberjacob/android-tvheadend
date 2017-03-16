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

package ie.macinnes.tvheadend.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Authenticator;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.sync.EpgSyncService;
import ie.macinnes.tvheadend.sync.EpgSyncTask;

public class AutomaticSetup {
    private static final String TAG = "AutoSetup";
    private static final String ACCOUNT_TYPE = "ie.macinnes.tvheadend";

    private AccountManager mAccountManager;
    private Context mContext;
    private final String accountName;
    private final String accountPassword;
    private final String accountHostname;
    private final int accountHtspPort;
    private final List<Listener> mListeners = new ArrayList<>();

    AutomaticSetup(
            @NonNull Context context,
            @NonNull String accountName,
            @NonNull String accountPassword,
            @NonNull String accountHostname,
            int accountHtspPort
    ) {
        mContext = context;
        this.accountName = accountName;
        this.accountPassword = accountPassword;
        this.accountHostname = accountHostname;
        this.accountHtspPort = accountHtspPort;

        mAccountManager = AccountManager.get(mContext);
    }

    void startSetup() {
        setState(State.STARTING);

        HtspConnection.ConnectionDetails connectionDetails = new HtspConnection.ConnectionDetails(accountHostname, accountHtspPort, accountName, accountPassword, "android-tvheadend (auth)", BuildConfig.VERSION_NAME);
        SimpleHtspConnection mConnection = new SimpleHtspConnection(connectionDetails);

        class ConnectionListener implements Authenticator.Listener, EpgSyncTask.Listener {
            private final HandlerThread mHandlerThread;
            private final Handler mHandler;
            private final SimpleHtspConnection mConnection;
            private final String accountName;
            private final String accountPassword;
            private final String accountHostname;
            private final int accountHtspPort;

            private ConnectionListener(SimpleHtspConnection mConnection, String accountName, String accountPassword, String accountHostname, int accountHtspPort) {
                this.mConnection = mConnection;
                this.accountName = accountName;
                this.accountPassword = accountPassword;
                this.accountHostname = accountHostname;
                this.accountHtspPort = accountHtspPort;

                mHandlerThread = new HandlerThread("EpgSyncService Handler Thread");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());
            }

            @Override
            public void onAuthenticationStateChange(@NonNull Authenticator.State state) {
                if (state == Authenticator.State.AUTHENTICATED) {
                    setState(State.CONNECTED);
                    final Account account = new Account(accountName, ACCOUNT_TYPE);

                    Bundle userdata = new Bundle();

                    userdata.putString(Constants.KEY_HOSTNAME, accountHostname);
                    userdata.putString(Constants.KEY_HTSP_PORT, String.valueOf(accountHtspPort));
                    //userdata.putString(Constants.KEY_HTTP_PORT, String.valueOf(accountHttpPort));
                    //userdata.putString(Constants.KEY_HTTP_PATH, accountHttpPath);

                    mAccountManager.addAccountExplicitly(account, accountPassword, userdata);

                    // Store the result, with the username too
                    userdata.putString(Constants.KEY_USERNAME, accountName);

                    // Move to the CompletedFragment
                    completeSetup();
                } else if (state == Authenticator.State.FAILED) {
                    // Close the connection, it's no longer needed
                    mConnection.stop();

                    Log.w(TAG, "Failed to validate credentials");

                    Bundle args = new Bundle();
                    args.putString(Constants.KEY_ERROR_MESSAGE, "Failed to validate HTSP Credentials");

                    // Move to the failed step
                    setState(State.FAILED);
                }
            }

            @Override
            public Handler getHandler() {
                return mHandler;
            }

            @Override
            public void onInitialSyncCompleted() {
                Log.d(TAG, "Initial Sync Completed");

                // Re-Start EPG sync service (removing quick-sync)
                Intent intent = new Intent(mContext, EpgSyncService.class);
                mContext.stopService(intent);
                mContext.startService(intent);

                MiscUtils.setSetupComplete(mContext, true);
                setState(State.COMPLETE);
            }
        }

        ConnectionListener mConnectionListener = new ConnectionListener(mConnection, accountName, accountPassword, accountHostname, accountHtspPort);
        mConnection.addAuthenticationListener(mConnectionListener);

        EpgSyncTask mEpgSyncTask = new EpgSyncTask(mContext, mConnection, true);
        mEpgSyncTask.addEpgSyncListener(mConnectionListener);

        mConnection.addAuthenticationListener(mEpgSyncTask);
        mConnection.addMessageListener(mEpgSyncTask);

        mConnection.start();
    }

    private void completeSetup() {
        ////String session = Constants.SESSION_MEDIA_PLAYER;
        //String session = Constants.SESSION_EXO_PLAYER;

        //SharedPreferences sharedPreferences = mContext.getSharedPreferences(
        //        Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        //SharedPreferences.Editor editor = sharedPreferences.edit();
        //editor.putString(Constants.KEY_SESSION, session);
        //editor.apply();
    }

    interface Listener {
        Handler getHandler();
        void onSetupStateChange(@NonNull State state);
    }

    enum State {
        STARTING,
        CONNECTED,
        COMPLETE,
        FAILED
    }

    void addSetupListener(Listener listener) {
        if (mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate setup listener");
            return;
        }
        mListeners.add(listener);
    }

    public void removeSetupListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to remove non existing setup listener");
            return;
        }
        mListeners.remove(listener);
    }

    private void setState(final State state) {
        for (final Listener listener : mListeners) {
            Handler handler = listener.getHandler();
            if (handler == null) {
                listener.onSetupStateChange(state);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSetupStateChange(state);
                    }
                });
            }
        }
    }
}
