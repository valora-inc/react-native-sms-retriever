package me.furtado.smsretriever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

public final class SmsBroadcastReceiver extends BroadcastReceiver {

    private SmsReceiveListener mSmsListener;

    public void setSmsListener(SmsReceiveListener smsListener) {
        mSmsListener = smsListener;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            return;
        }

        if (mSmsListener == null) {
            return;
        }

        final Bundle extras = intent.getExtras();
        if (extras == null) {
            mSmsListener.onSmsError("Extras is null");
            return;
        }

        final Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
        if (status == null) {
            mSmsListener.onSmsError("Status is null");
            return;
        }

        switch (status.getStatusCode()) {
            case CommonStatusCodes.SUCCESS:
                String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                mSmsListener.onSmsReceived(message);
                break;

            case CommonStatusCodes.TIMEOUT:
                // Waiting for SMS timed out (5 minutes)
                mSmsListener.onSmsTimeout();
                break;

            case CommonStatusCodes.API_NOT_CONNECTED:
                mSmsListener.onSmsError("Api not connected");
                break;

            case CommonStatusCodes.NETWORK_ERROR:
                mSmsListener.onSmsError("Network error");
                break;

            default:
            case CommonStatusCodes.ERROR:
                mSmsListener.onSmsError("Unknown error");
                break;
        }
    }

    public interface SmsReceiveListener {
        void onSmsReceived(String message);
        void onSmsTimeout();
        void onSmsError(String error);
    }
}
