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

public class AutoSetupActivityReceiver extends BroadcastReceiver {
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
        //TODO: Null checking

        AutomaticSetup automaticSetup = new AutomaticSetup(context, accountName, accountPassword, accountHostname, accountHtspPort, accountHttpPort, accountHttpPath);
        automaticSetup.addSetupListener(new AutomaticSetup.Listener() {
            @Override
            public Handler getHandler() {
                return null;
            }

            @Override
            public void onSetupStateChange(@NonNull AutomaticSetup.State state) {
                if (state == AutomaticSetup.State.FAILED) {
                    //TODO: Something
                } else if (state == AutomaticSetup.State.COMPLETE) {
                    //TODO: Something else
                }
            }
        });
    }
}
