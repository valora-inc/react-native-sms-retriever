package me.furtado.smsretriever;

import android.content.IntentFilter;
import android.util.Log;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

final class SmsHelper implements SmsBroadcastReceiver.SmsReceiveListener {

    private static final String TAG = "SmsHelper";
    private static final String SMS_EVENT = "me.furtado.smsretriever:SmsEvent";
    private static final String TASK_FAILURE_ERROR_TYPE = "TASK_FAILURE_ERROR_TYPE";
    private static final String TASK_FAILURE_ERROR_MESSAGE = "Failed to start SMS retriever";

    private static final String MESSAGE_KEY = "message";
    private static final String TIMEOUT_KEY = "timeout";
    private static final String ERROR_KEY = "error";

    private final ReactApplicationContext mContext;

    private SmsBroadcastReceiver mReceiver;
    private Promise mPromise;
    private int mNumMessages;

    SmsHelper(@NonNull final ReactApplicationContext context) {
        mContext = context;
    }

    //region - public

    public void startRetriever(final int numMessages, final Promise promise) {
        mPromise = promise;
        mNumMessages = numMessages;

        Log.d(TAG, "Attempting to retrieve " + numMessages + " sms messages");

        if (!GooglePlayServicesHelper.isAvailable(mContext)) {
            promiseReject(GooglePlayServicesHelper.UNAVAILABLE_ERROR_TYPE, GooglePlayServicesHelper.UNAVAILABLE_ERROR_MESSAGE);
            return;
        }

        if (!GooglePlayServicesHelper.hasSupportedVersion(mContext)) {
            promiseReject(GooglePlayServicesHelper.UNSUPORTED_VERSION_ERROR_TYPE, GooglePlayServicesHelper.UNSUPORTED_VERSION_ERROR_MESSAGE);
            return;
        }

        registerReceiver();
        startSmsRetrieverTasks();
        promiseResolve(true);
    }

    public void onSmsReceived(final String message) {
        if (message == null || message.isEmpty()) {
            Log.w(TAG, "Received empty sms message");
        }
        else {
            Log.d(TAG, "Received sms message");
            mNumMessages -= 1;
        }

        emitJSEvent(MESSAGE_KEY, message);

        if (mNumMessages <= 0) {
            Log.d(TAG, "Last message received, unregistering receiver");
            unregisterReceiver();
        }
        else {
            Log.d(TAG, "Num remaining messages: " + mNumMessages);
        }
    }

    public void onSmsTimeout() {
        emitJSEvent(TIMEOUT_KEY, "Sms Retriever Timeout");
    }

    public void onSmsError(final String error) {
        emitJSEvent(ERROR_KEY, error);
    }

    //endregion

    //region - Privates

    private void startSmsRetrieverTasks() {
        Log.d(TAG, "Attempting to start SmsRetriever client");
        final SmsRetrieverClient client = SmsRetriever.getClient(mContext);

        for (int i = 0; i < mNumMessages; i++) {
            final Task<Void> task = client.startSmsRetriever();

            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "SmsRetriver task started successfully");
                }
            });

            task.addOnFailureListener(new OnFailureListener() {	            
                @Override	        
                public void onFailure(@NonNull Exception e) {	
                    Log.e(TAG, "Failed to start SmsRetriever client", e);	
                }	
            });
        };
    }

    private void registerReceiver() {
        // already registerted
        if (mReceiver != null) {
          return;
        }

        try {
            mReceiver = new SmsBroadcastReceiver();
            mReceiver.setSmsListener(this);
            final IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
            mContext.registerReceiver(mReceiver, intentFilter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register broadcast receiver", e);
        }
    }

    private void unregisterReceiver() {
        if (mReceiver == null) {
            return;
        }

        try {
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister broadcast receiver", e);
        }
    }

    private void promiseResolve(@NonNull final Object value) {
        if (mPromise != null) {
            mPromise.resolve(value);
            mPromise = null;
        }
    }

    private void promiseReject(@NonNull final String type, @NonNull final String message) {
        if (mPromise != null) {
            mPromise.reject(type, message);
            mPromise = null;
        }
    }

    private void emitJSEvent(@NonNull final String key, final String message) {
        if (mContext == null) {
            return;
        }

        if (!mContext.hasActiveCatalystInstance()) {
            return;
        }

        WritableNativeMap map = new WritableNativeMap();
        map.putString(key, message);

        mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(SMS_EVENT, map);
    }

    //endregion

}
