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

/*
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

public class SetupReceiver extends BroadcastReceiver {
    private static final String TAG = "SetupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent outIntent = new Intent(context, AutomaticSetup.class);
        outIntent.putExtra("intent", intent);
        context.startActivity(outIntent);

        String accountName = intent.getStringExtra("accountName");
        String accountPassword = intent.getStringExtra("accountPassword");
        String accountHostname = intent.getStringExtra("accountHostname");
        int accountHtspPort = intent.getIntExtra("accountHtspPort", -1);
        int accountHttpPort = intent.getIntExtra("accountHttpPort", -1);
        String accountHttpPath = intent.getStringExtra("accountHttpPath");
        if (accountName == null) {
            throw new IllegalArgumentException("accountName cannot be null");
        } else if (accountPassword == null) {
            throw new IllegalArgumentException("accountPassword cannot be null");
        } else if (accountHostname == null) {
            throw new IllegalArgumentException("accountHostname cannot be null");
        } else if (accountHtspPort == -1) {
            throw new IllegalArgumentException("accountHtspPort cannot be null");
        } else if (accountHttpPort == -1) {
            throw new IllegalArgumentException("accountHttpPort cannot be null");
        } else if (accountHttpPath == null) {
            throw new IllegalArgumentException("accountHttpPath cannot be null");
        }

        AutomaticSetup automaticSetup = new AutomaticSetup(context, accountName, accountPassword, accountHostname, accountHtspPort, accountHttpPort, accountHttpPath);
        automaticSetup.addSetupListener(new SetupListener(context));
        automaticSetup.startSetup();
    }

    private class SetupListener implements AutomaticSetup.Listener {
        Context mContext;

        SetupListener(Context context) {
            mContext = context;
        }

        @Override
        public Handler getHandler() {
            return null;
        }

        @Override
        public void onSetupStateChange(@NonNull AutomaticSetup.State state) {
            if (state == AutomaticSetup.State.FAILED) {
                Log.e(TAG, "Automatic setup failed!");
                Intent failureIntent = new Intent();
                failureIntent.setAction("ie.macinnes.tvheadend.autoSetup.failure");
                mContext.sendBroadcast(failureIntent);

            } else if (state == AutomaticSetup.State.COMPLETE) {
                Log.i(TAG, "Automatic setup succeeded!");
                Intent successIntent = new Intent();
                successIntent.setAction("ie.macinnes.tvheadend.autoSetup.success");
                mContext.sendBroadcast(successIntent);
            }
        }
    }
}
