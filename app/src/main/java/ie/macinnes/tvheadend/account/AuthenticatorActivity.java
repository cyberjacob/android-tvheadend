/* Copyright 2016 Kiall Mac Innes <kiall@macinnes.ie>

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.
*/
package ie.macinnes.tvheadend.account;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Authenticator;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;

public class AuthenticatorActivity extends AccountAuthenticatorActivity {
    private static final String TAG = AuthenticatorActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GuidedStepFragment fragment = new ServerFragment();
        fragment.setArguments(getIntent().getExtras());
        GuidedStepFragment.addAsRoot(this, fragment, android.R.id.content);
    }

    @Override
    public void onBackPressed() {
        if (GuidedStepFragment.getCurrentGuidedStepFragment(getFragmentManager())
                instanceof CompletedFragment) {
            finish();

        } else if (GuidedStepFragment.getCurrentGuidedStepFragment(getFragmentManager())
                instanceof FailedFragment) {
            finish();

        } else {
            super.onBackPressed();
        }
    }

    public abstract static class BaseGuidedStepFragment extends GuidedStepFragment {
        protected AccountManager mAccountManager;

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard_Account;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAccountManager = AccountManager.get(getActivity());
        }
    }

    public static class ServerFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_HOSTNAME = 1;
        private static final int ACTION_ID_HTSP_PORT = 2;
        private static final int ACTION_ID_NEXT = 3;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.server_name),
                    getString(R.string.server_name_body), null, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HOSTNAME)
                    .title(R.string.server_ip)
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HTSP_PORT)
                    .title(R.string.server_htsp_port)
                    .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);
        }

        @Override
        public void onCreateButtonActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_NEXT)
                    .title(R.string.setup_continue)
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == ACTION_ID_NEXT) {
                Bundle args = getArguments();

                // Hostname Field
                GuidedAction hostnameAction = findActionById(ACTION_ID_HOSTNAME);
                CharSequence hostnameValue = hostnameAction.getDescription();

                if (hostnameValue == null || TextUtils.isEmpty(hostnameValue)) {
                    Toast.makeText(getActivity(), R.string.server_ip_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }

                args.putString(Constants.KEY_HOSTNAME, hostnameValue.toString());

                // HTSP Port Field
                GuidedAction htspPortAction = findActionById(ACTION_ID_HTSP_PORT);
                CharSequence htspPortValue = htspPortAction.getDescription();

                if (htspPortValue == null || TextUtils.isEmpty(htspPortValue)) {
                    Toast.makeText(getActivity(), R.string.server_htsp_port_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }

                args.putString(Constants.KEY_HTSP_PORT, htspPortAction.getDescription().toString());

                // Move to the next setup
                GuidedStepFragment fragment = new AccountFragment();
                fragment.setArguments(args);
                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class AccountFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_USERNAME = 1;
        private static final int ACTION_ID_PASSWORD = 2;
        private static final int ACTION_ID_ADD_ACCOUNT = 3;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.account_name),
                    getString(R.string.account_name_body), null, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_USERNAME)
                    .title(R.string.account_username)
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PASSWORD)
                    .title(R.string.account_password)
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);
        }

        @Override
        public void onCreateButtonActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_ADD_ACCOUNT)
                    .title(R.string.setup_account_finish)
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == ACTION_ID_ADD_ACCOUNT) {
                Bundle args = getArguments();

                // Username Field
                GuidedAction usernameAction = findActionById(ACTION_ID_USERNAME);
                CharSequence usernameValue = usernameAction.getDescription();

                if (usernameValue == null || TextUtils.isEmpty(usernameValue)) {
                    Toast.makeText(getActivity(), R.string.account_username_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }

                args.putString(Constants.KEY_USERNAME, usernameAction.getDescription().toString());

                // Password Field
                GuidedAction passwordAction = findActionById(ACTION_ID_PASSWORD);
                CharSequence passwordValue = passwordAction.getDescription();

                if (passwordValue == null || TextUtils.isEmpty(passwordValue)) {
                    Toast.makeText(getActivity(), R.string.account_password_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }

                args.putString(Constants.KEY_PASSWORD, passwordAction.getDescription().toString());

                // Move to the next step
                GuidedStepFragment fragment = new ValidateHTSPAccountFragment();
                fragment.setArguments(args);
                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class ValidateHTSPAccountFragment extends BaseGuidedStepFragment implements
            HtspConnection.Listener, Authenticator.Listener{
        private static final int ACTION_ID_PROCESSING = 1;

        private SimpleHtspConnection mConnection;

        protected String mAccountType;
        protected String mAccountName;
        protected String mAccountPassword;
        protected String mAccountHostname;
        protected String mAccountHtspPort;

        @Override
        public GuidedActionsStylist onCreateActionsStylist() {
            return new GuidedActionsStylist() {
                @Override
                public int onProvideItemLayoutId() {
                    return R.layout.setup_progress;
                }

            };
        }

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard_Account_NoSelector;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.account_name),
                    getString(R.string.setup_progress_title), null, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PROCESSING)
                    .title(R.string.setup_progress_body)
                    .infoOnly(true)
                    .build();
            actions.add(action);
        }

        @Override
        public void onStart() {
            super.onStart();

            Bundle args = getArguments();

            mAccountType = args.getString(AccountManager.KEY_ACCOUNT_TYPE);
            mAccountName = args.getString(Constants.KEY_USERNAME);
            mAccountPassword = args.getString(Constants.KEY_PASSWORD);
            mAccountHostname = args.getString(Constants.KEY_HOSTNAME);
            mAccountHtspPort = args.getString(Constants.KEY_HTSP_PORT);

            HtspConnection.ConnectionDetails connectionDetails = new HtspConnection.ConnectionDetails(
                    mAccountHostname, Integer.parseInt(mAccountHtspPort), mAccountName,
                    mAccountPassword, "android-tvheadend (auth)", BuildConfig.VERSION_NAME);

            mConnection = new SimpleHtspConnection(connectionDetails);
            mConnection.addConnectionListener(this);
            mConnection.addAuthenticationListener(this);
            mConnection.start();
        }

        @Override
        public void onStop() {
            super.onStop();

            mConnection.stop();
            mConnection.removeConnectionListener(this);
            mConnection.removeAuthenticationListener(this);
            mConnection = null;
        }

        @Override
        public Handler getHandler() {
            return null;
        }

        @Override
        public void setConnection(@NonNull HtspConnection htspConnection) {

        }

        @Override
        public void onConnectionStateChange(@NonNull HtspConnection.State state) {
            if (state == HtspConnection.State.FAILED) {
                Log.w(TAG, "Failed to connect to HTSP server");

                Bundle args = getArguments();
                args.putString(Constants.KEY_ERROR_MESSAGE, getString(R.string.setup_htsp_failed));

                // Move to the failed step
                GuidedStepFragment fragment = new FailedFragment();
                fragment.setArguments(args);
                add(getFragmentManager(), fragment);
            }
        }

        @Override
        public void onAuthenticationStateChange(@NonNull Authenticator.State state) {
            if (state == Authenticator.State.AUTHENTICATED) {
                // Store the account
                final Account account = new Account(mAccountName, mAccountType);

                Bundle userdata = new Bundle();

                userdata.putString(Constants.KEY_HOSTNAME, mAccountHostname);
                userdata.putString(Constants.KEY_HTSP_PORT, mAccountHtspPort);

                mAccountManager.addAccountExplicitly(account, mAccountPassword, userdata);

                // Store the result, with the username too
                userdata.putString(Constants.KEY_USERNAME, mAccountName);

                AuthenticatorActivity activity = (AuthenticatorActivity) getActivity();
                activity.setAccountAuthenticatorResult(userdata);

                // Move to the CompletedFragment
                GuidedStepFragment fragment = new CompletedFragment();
                fragment.setArguments(getArguments());
                add(getFragmentManager(), fragment);
            } else if (state == Authenticator.State.FAILED) {
                Log.w(TAG, "Failed to validate credentials");

                Bundle args = getArguments();
                args.putString(Constants.KEY_ERROR_MESSAGE, getString(R.string.setup_failed_htsp));

                // Move to the failed step
                GuidedStepFragment fragment = new FailedFragment();
                fragment.setArguments(args);
                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class CompletedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

            return new GuidanceStylist.Guidance(
                    getString(R.string.account_name),
                    getString(R.string.account_add_successful),
                    null,
                    null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title(R.string.setup_complete_action_title)
                    .description(R.string.setup_account_complete_body)
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            getActivity().finish();
        }
    }

    public static class FailedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

            return new GuidanceStylist.Guidance(
                    getString(R.string.account_name),
                    getString(R.string.setup_complete_failed),
                    null,
                    null);
        }

        @Override
        public void onResume() {
            super.onResume();

            Bundle args = getArguments();
            String errorMessage = args.getString(Constants.KEY_ERROR_MESSAGE);
            getGuidanceStylist().getDescriptionView().setText(getString(R.string.account_failed_error, errorMessage));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title(R.string.setup_complete_action_title)
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            getActivity().finish();
        }
    }
}
