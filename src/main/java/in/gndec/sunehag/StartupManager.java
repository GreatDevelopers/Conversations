package in.gndec.sunehag;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by jugraj on 19/11/16.
 */

public class StartupManager {
    SharedPreferences preferences;
    Context context;

    int PRIVATE_MODE = 0;

    private static final String PREFERENCE_NAME = "StartupManager";
    private static final String IS_FIRST_TIME_LAUNCH = "IsFirstTimeLaunch";

    public StartupManager(Context context){
        this.context = context;
        preferences = this.context.getSharedPreferences(PREFERENCE_NAME,PRIVATE_MODE);
    }

    public void setFirstTimeLaunch(boolean isFirstTime){
        preferences.edit().putBoolean(IS_FIRST_TIME_LAUNCH, isFirstTime).apply();
    }

    public boolean isFirstTimeLaunch(){
        return preferences.getBoolean(IS_FIRST_TIME_LAUNCH, true);
    }
}
