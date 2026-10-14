package com.limelight.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import com.limelight.R;
import com.limelight.setup.SetupWizardActivity;

/** MoonVibe: opens the first-run setup again. */
public class SetupWizardPreference extends Preference {
    public SetupWizardPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        context.startActivity(new Intent(context, SetupWizardActivity.class));
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
        }
    }
}
