package com.nitramite.paketinseuranta;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.multidex.MultiDex;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.nitramite.adapters.CustomEventsRecyclerViewAdapter;
import com.nitramite.adapters.CustomParcelsAdapterV2;
import com.nitramite.utils.LocaleUtils;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.wdullaer.swipeactionadapter.SwipeActionAdapter;
import com.wdullaer.swipeactionadapter.SwipeDirection;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MainMenu extends AppCompatActivity implements SwipeActionAdapter.SwipeActionListener, SwipeRefreshLayout.OnRefreshListener {

    //  Logging
    private static final String TAG = "MainMenu";

    // Main items
    private DatabaseHelper databaseHelper = new DatabaseHelper(this);
    private ArrayList<ParcelItem> parcelItems = new ArrayList<>();

    // Tracking data list views
    private ArrayList<String> trackingEvents_FIS = new ArrayList<>();
    private ArrayList<String> trackingEvents_TIMESTAMPS = new ArrayList<>();
    private ArrayList<String> trackingEvents_LOCATIONCODES = new ArrayList<>();
    private ArrayList<String> trackingEvents_LOCATIONNAMES = new ArrayList<>();

    // Components
    private LocaleUtils localeUtils = new LocaleUtils();
    private SwipeRefreshLayout swipeRefreshLayout;
    private CustomParcelsAdapterV2 adapter;
    private ListView trackItemsList;
    private SharedPreferences sharedPreferences;
    private Boolean SP_UPDATE_FAILED_FIRST = false;

    // Activity request codes
    private static final int ACTIVITY_RESULT_PARCEL = 1;  // The request code
    private static final int ACTIVITY_RESULT_ARCHIVE = 2;  // The request code
    public static final int ACTIVITY_RESULT_PARCEL_EDITOR = 3;  // The request code

    // Swipe action adapter
    protected SwipeActionAdapter mAdapter;
    private int onSwipePosition;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(localeUtils.updateBaseContextLocale(base));
        MultiDex.install(this);
    }


    // Parcel service finish broad cast receiver
    private BroadcastReceiver dataChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            int serviceMode = 0;
            String parcelID = "";
            String parcelCode = "";
            if (extras == null) {
                Log.i(TAG, "!No extras from service broadcast receiver!");
            } else {
                serviceMode = extras.getInt("MODE");
                parcelCode = extras.getString("PARCEL_CODE");
                parcelID = extras.getString("PARCEL_ID");
            }
            if (serviceMode == 0) {
                swipeRefreshLayout.setRefreshing(false);
                readItems();
            }
        }
    };


    // Package update animation receiver for each service service package update
    private BroadcastReceiver packageUpdateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                swipeRefreshLayout.setRefreshing(true);
                readItems(); // Refreshes list view
                if (sharedPreferences.getBoolean("SP_PACKAGE_UPDATE_NOTIFY_SNACKBAR", true)) {
                    if (extras.getString("TASK_PACKAGE_IDENTITY") != null) {
                        final Snackbar snackBar = Snackbar.make(findViewById(android.R.id.content), extras.getString("TASK_PACKAGE_IDENTITY") + " " + getString(R.string.main_menu_snackbar_message_updated), Snackbar.LENGTH_SHORT);
                        View view = snackBar.getView();
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                        params.gravity = Gravity.CENTER;
                        view.setLayoutParams(params);
                        TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
                        tv.setTextColor(Color.WHITE);
                        snackBar.show();
                    }
                }
            }
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        // Register finish receiver
        IntentFilter intentFilter = new IntentFilter("service_broadcast");
        LocalBroadcastManager.getInstance(this).registerReceiver(dataChangeReceiver, intentFilter);
        // Register update receiver
        IntentFilter packageIntentFilter = new IntentFilter("service_package_broadcast");
        LocalBroadcastManager.getInstance(this).registerReceiver(packageUpdateChangeReceiver, packageIntentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(dataChangeReceiver);
            if (sharedPreferences != null) {
                if (sharedPreferences.getBoolean(Constants.SP_PARCELS_AUTOMATIC_UPDATE, true)) {
                    if (!isMyServiceRunning(ParcelServiceTimer.class)) {
                        startService(new Intent(MainMenu.this, ParcelServiceTimer.class));
                    }
                }
            }
        } catch (IllegalStateException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataChangeReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set theme
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (sharedPreferences.getString(Constants.SP_THEME_SELECTION, "").equals("2")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.BLACK);
            }
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);


        // Screen orientation setting
        if (sharedPreferences.getBoolean(Constants.SP_PORTRAIT_ORIENTATION_ONLY, false)) {
            super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }


        // Install security provider, which is not supported by android kitkat for ssl connections
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            try {
                ProviderInstaller.installIfNeeded(getApplicationContext());
            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
        }


        //startActivity(new Intent(MainMenu.this, Archive.class));
        // Stop service if running
        stopService(new Intent(MainMenu.this, ParcelService.class));

        // Shared preferences
        boolean SP_UPDATE_PARCELS_ON_STARTUP = sharedPreferences.getBoolean(Constants.SP_UPDATE_PARCELS_ON_STARTUP, true);
        SP_UPDATE_FAILED_FIRST = sharedPreferences.getBoolean(Constants.SP_UPDATE_FAILED_FIRST, false);
        boolean autoParcelUpdates = sharedPreferences.getBoolean("parcels_automatic_update", true);
        // If automatic background updating is disabled ensure that service is not running
        boolean SP_PARCELS_AUTOMATIC_UPDATE = sharedPreferences.getBoolean(Constants.SP_PARCELS_AUTOMATIC_UPDATE, true);
        if (!SP_PARCELS_AUTOMATIC_UPDATE) {
            if (isMyServiceRunning(ParcelServiceTimer.class)) {
                stopService(new Intent(MainMenu.this, ParcelServiceTimer.class));
            }
        }


        // Intro logic, show intro if not shown yet
        if (!sharedPreferences.getBoolean(Constants.SP_INTRO_IS_SHOWN, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(MainMenu.this, Intro.class));
        }


        // -----------------------------------------------------------------------------------------

        // Status bar tint
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus(true);
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setStatusBarTintResource(R.color.colorPrimary);
        }

        // -----------------------------------------------------------------------------------------

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        trackItemsList = findViewById(R.id.trackItemsList);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // Floating action buttons
        FloatingActionButton floatingAddParcelBtn = findViewById(R.id.floatingAddParcelBtn);
        floatingAddParcelBtn.setOnClickListener(view -> {
            Intent intent = new Intent(MainMenu.this, ParcelEditor.class);
            startActivityForResult(intent, ACTIVITY_RESULT_PARCEL_EDITOR);
        });


        readItems();
        setupListViewListener();
        setupListViewListener2();


        // Start automatic refresh task if enabled from settings
        if (SP_UPDATE_PARCELS_ON_STARTUP) {
            refreshParcelDataTask();
        }

        // Stop auto parcel updates service if disabled
        if (!autoParcelUpdates) {
            stopService(new Intent(this, ParcelServiceTimer.class));
        }


        // Backup feature
        checkForAutomaticBackup();


        Log.i(TAG, "-------------- SERVICE CHECK --------------");
        Log.i(TAG, "ParcelServiceTimer.class running: " + isMyServiceRunning(ParcelServiceTimer.class));
        Log.i(TAG, "-------------------------------------------");
    } // End of onCreate();

    // ---------------------------------------------------------------------------------------------

    // Status bar tinting
    @TargetApi(19)
    private void setTranslucentStatus(boolean on) {
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        final int bits = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }


    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        swipeRefreshLayout.setRefreshing(false);
        if (requestCode == ACTIVITY_RESULT_PARCEL || requestCode == ACTIVITY_RESULT_ARCHIVE || requestCode == ACTIVITY_RESULT_PARCEL_EDITOR) {
            readItems();
        }
        try {
            // If parcel id is provided, update it right away
            final String parcelId = data.getStringExtra("PARCEL_ID");
            if (requestCode == ACTIVITY_RESULT_PARCEL_EDITOR && parcelId != null) {
                // Start service
                Intent intent = new Intent(this, ParcelService.class);
                intent.putExtra("MODE", 0);
                intent.putExtra("PARCEL_ID", parcelId);
                startService(intent);
            }
        } catch (NullPointerException ignored) {
        }
    }


    // ---------------------------------------------------------------------------------------------

    // ON CLICK LISTENER
    private void setupListViewListener2() {
        trackItemsList.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String parcelId = parcelItems.get(position).getParcelId();
                    Intent intent = new Intent(MainMenu.this, Parcel.class);
                    intent.putExtra("PARCEL_ID", parcelId);
                    startActivityForResult(intent, ACTIVITY_RESULT_PARCEL);
                });
    }


    // ---------------------------------------------------------------------------------------------

    // Long click listener for Parcel events path window service starter
    private void setupListViewListener() {
        trackItemsList.setOnItemLongClickListener(
                (adapter, item, pos, id) -> {

                    String[] longTapArrayItems = getResources().getStringArray(R.array.pref_long_tap_values);
                    String longTapSelectedAction = sharedPreferences.getString(Constants.SP_MAIN_MENU_LIST_LONG_TAP_ACTION, "");

                    if (longTapSelectedAction.equals(longTapArrayItems[0])) {
                        if (isMyServiceRunning(ParcelService.class)) {
                            Toast.makeText(MainMenu.this, R.string.main_menu_update_in_progress_try_again_later, Toast.LENGTH_LONG).show();
                        } else {
                            trackingEvents_FIS.clear();
                            trackingEvents_TIMESTAMPS.clear();
                            trackingEvents_LOCATIONCODES.clear();
                            trackingEvents_LOCATIONNAMES.clear();
                            Cursor res = databaseHelper.getAllTrackingData(parcelItems.get(pos).getParcelId());
                            if (res.getCount() == 0) {
                                Log.i(TAG, "Got nothing saved on tracking table with parcelID: " + parcelItems.get(pos).getParcelCode());
                            }
                            while (res.moveToNext()) {
                                trackingEvents_FIS.add(res.getString(3));
                                trackingEvents_TIMESTAMPS.add(res.getString(4));
                                trackingEvents_LOCATIONCODES.add(res.getString(6));
                                trackingEvents_LOCATIONNAMES.add(res.getString(7));
                            }
                            res.close();
                            showPackageEventsDialog();
                        }
                    } else if (longTapSelectedAction.equals(longTapArrayItems[1])) { // Clipboard
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("", parcelItems.get(pos).getParcelCode());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(MainMenu.this, R.string.main_menu_code_copied_to_your_clipboard, Toast.LENGTH_SHORT).show();
                    } else if (longTapSelectedAction.equals("") || longTapSelectedAction.equals(longTapArrayItems[2])) { // Edit information
                        Intent intent = new Intent(MainMenu.this, ParcelEditor.class);
                        intent.putExtra("PARCEL_ID", parcelItems.get(pos).getParcelId());
                        startActivityForResult(intent, ACTIVITY_RESULT_PARCEL_EDITOR);
                    } else {
                        Toast.makeText(MainMenu.this, R.string.main_menu_long_click_function_selection_not_made_on_settings, Toast.LENGTH_LONG).show();
                    }
                    return true;
                });
    }


    // Shows package events dialog
    public void showPackageEventsDialog() {
        if (trackingEvents_FIS.size() > 0) {
            final Dialog dialog = new Dialog(MainMenu.this);
            dialog.setTitle("Tapahtumat");
            dialog.setContentView(R.layout.custom_events_recycler_view);
            dialog.setCanceledOnTouchOutside(false);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
            CustomEventsRecyclerViewAdapter customEventsRecyclerViewAdapter = new CustomEventsRecyclerViewAdapter(trackingEvents_FIS, trackingEvents_TIMESTAMPS, trackingEvents_LOCATIONCODES, trackingEvents_LOCATIONNAMES);
            RecyclerView customEventsRecyclerView = dialog.findViewById(R.id.customEventsRecyclerView);
            customEventsRecyclerView.setAdapter(customEventsRecyclerViewAdapter);
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
            linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            customEventsRecyclerView.setLayoutManager(linearLayoutManager);
        } else {
            Toast.makeText(this, R.string.main_menu_did_not_get_event_statuses_try_again_later, Toast.LENGTH_SHORT).show();
        }
    }


    // ---------------------------------------------------------------------------------------------


    public void refreshParcelDataTask() {
        try {
            if (!isMyServiceRunning(ParcelService.class)) {
                // Start service
                Intent intent = new Intent(this, ParcelService.class);
                intent.putExtra("MODE", 0);
                intent.putExtra("NOTIFICATIONS", false); // Normally false
                intent.putExtra("PARCEL_CODE", "");
                intent.putExtra("PARCEL_ID", "");
                intent.putExtra("UPDATE_FAILED_FIRST", SP_UPDATE_FAILED_FIRST);
                intent.putExtra("COURIER_ICONS_ENABLED", true);
                intent.putExtra("START_AS_FOREGROUND_SERVICE", false);
                startService(intent);
            }
            readItems();
        } catch (RuntimeException ignored) {
        }
    }


    // Get parcel items
    private void readItems() {
        parcelItems.clear();
        Cursor res = databaseHelper.getAllDataWithLatestEvent();
        while (res.moveToNext()) {
            parcelItems.add(new ParcelItem(
                    res.getString(0),
                    res.getString(1),
                    res.getString(2),
                    res.getString(3),
                    res.getString(4),
                    res.getString(5),       // Last update status
                    res.getString(6),       // Latest event description
                    res.getString(7),       // Parcel carrier
                    res.getString(8),
                    res.getString(9),
                    res.getString(10),
                    res.getString(11),
                    res.getString(12)       // Last pickup date
            ));
        }
        updateListView();
    }


    // ---------------------------------------------------------------------------------------------

    // Update list view
    public void updateListView() {
        if (adapter == null) {
            adapter = new CustomParcelsAdapterV2(MainMenu.this, parcelItems, true);
            mAdapter = new SwipeActionAdapter(adapter);
            mAdapter.setSwipeActionListener(this)
                    .setDimBackgrounds(true)
                    .setListView(trackItemsList);
            mAdapter.addBackground(SwipeDirection.DIRECTION_NORMAL_RIGHT, R.layout.swipe_right_archive)
                    .addBackground(SwipeDirection.DIRECTION_NORMAL_LEFT, R.layout.swipe_left_delete);
            trackItemsList.setAdapter(mAdapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean hasActions(int position, SwipeDirection direction) {
        if (direction.isLeft()) return true;
        if (direction.isRight()) return true;
        return false;
    }

    @Override
    public boolean shouldDismiss(int position, SwipeDirection direction) {
        return direction == SwipeDirection.DIRECTION_NORMAL_RIGHT;
    }

    @Override
    public void onSwipe(int[] positionList, SwipeDirection[] directionList) {
        for (int i = 0; i < positionList.length; i++) {
            SwipeDirection direction = directionList[i];
            onSwipePosition = positionList[i];
            switch (direction) {
                case DIRECTION_NORMAL_RIGHT:
                    directionRightArchive();
                    break;
                case DIRECTION_NORMAL_LEFT:
                    directionLeftDelete();
                    break;
                case DIRECTION_FAR_RIGHT:
                    directionRightArchive();
                    break;
                case DIRECTION_FAR_LEFT:
                    directionLeftDelete();
                    break;
            }
        }
    }


    @Override
    public void onSwipeStarted(ListView listView, int position, SwipeDirection direction) {
        swipeRefreshLayout.setEnabled(false);
    }

    @Override
    public void onSwipeEnded(ListView listView, int position, SwipeDirection direction) {
        swipeRefreshLayout.setEnabled(true);
    }


    // Swipe from right to left deletes item
    private void directionLeftDelete() {
        if (isMyServiceRunning(ParcelService.class)) {
            Toast.makeText(MainMenu.this, R.string.main_menu_cannot_delete_updating_packages_data, Toast.LENGTH_LONG).show();
        } else {
            deleteItemConfirmationDialog();
        }
    }


    // Swipe from left to right deletes item
    private void directionRightArchive() {
        String codeId = parcelItems.get(onSwipePosition).getParcelId();
        databaseHelper.updateArchived(codeId, true);
        readItems();
    }


    private void deleteItemConfirmationDialog() {
        new AlertDialog.Builder(MainMenu.this)
                .setTitle(R.string.main_menu_deletion_title)
                .setMessage(R.string.main_menu_you_are_about_to_delete_following_package_from_list)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    String codeId = parcelItems.get(onSwipePosition).getParcelId();
                    databaseHelper.deletePackageData(codeId);
                    readItems();
                })
                .setNegativeButton(android.R.string.no, (dialog, which) -> {
                    // Return
                })
                .setIcon(R.mipmap.ps_logo_round)
                .show();
    }


    // Check if service is running
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }


    // About dialog
    private void aboutDialog() {
        String appVersionCode = "-";
        String appVersionName = "-";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersionCode = String.valueOf(pInfo.versionCode);
            appVersionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        final String versionDetails =
                getString(R.string.main_menu_version_details) + "\n" +
                        getString(R.string.main_menu_version_code) + " " + appVersionCode + "\n" +
                        getString(R.string.main_menu_version_name) + " " + appVersionName;

        FancyAlertDialog.Builder aboutDialog = new FancyAlertDialog.Builder(MainMenu.this)
                // .setImageRecourse(R.mipmap.ps_logo_round)
                .setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ps_logo_round))
                .setTextTitle(R.string.main_menu_about_title)
                .setTextSubTitle(R.string.main_menu_about_sub_title)
                .setBody(getString(R.string.main_menu_about_body) + " " +
                        getString(R.string.main_menu_body_part_two) +
                        "\n\n" + versionDetails
                )
                // .setNegativeColor(R.color.colorPrimary)
                .setNegativeButtonText(R.string.main_menu_close)
                .setOnNegativeClicked((view, dialog) -> dialog.dismiss())
                .setUserManualButtonText(R.string.menu_menu_user_manual)
                .setOnUserManualClicked((view, dialog) -> {
                    String url = "http://www.nitramite.com/paketin-seuranta.html";
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                })
                /* .setAutoHide(true)*/
                .build();
        aboutDialog.show();
    }


    // ---------------------------------------------------------------------------------------------

    // Refresh Parcel's data
    private void refreshParcels() {
        if (isMyServiceRunning(ParcelService.class)) {
            Toast.makeText(MainMenu.this, R.string.main_menu_update_in_progress_try_again_later, Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(true);
        } else {
            Cursor res = databaseHelper.getAllData();
            int TaskCount = res.getCount();
            if (TaskCount == 0) {
                Toast.makeText(MainMenu.this, R.string.main_menu_list_is_empty_nothing_to_update, Toast.LENGTH_LONG).show();
                swipeRefreshLayout.setRefreshing(false);
            } else {
                final Snackbar snackBar = Snackbar.make(findViewById(android.R.id.content), R.string.main_menu_updating, Snackbar.LENGTH_LONG);
                View view = snackBar.getView();
                TextView tv = view.findViewById(android.support.design.R.id.snackbar_text);
                tv.setTextColor(Color.WHITE);
                snackBar.show();
                refreshParcelDataTask();
            }
        }
    }


    @Override
    public void onRefresh() {
        refreshParcels();
    }

    // MENU ITEMS
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_barcode_list || id == R.id.action_barcode_list_icon) {
            if (parcelItems.size() > 0) {
                startActivity(new Intent(MainMenu.this, BarcodeList.class));
            } else {
                Toast.makeText(this, R.string.main_menu_your_list_is_empty, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_archive || id == R.id.action_archive_icon) {
            startActivityForResult(new Intent(MainMenu.this, Archive.class), ACTIVITY_RESULT_ARCHIVE);
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(MainMenu.this, MyPreferencesActivity.class));
            return true;
        }
        if (id == R.id.action_about) {
            aboutDialog();
            return true;
        }
        if (id == R.id.action_database_dump) {
            databaseBackupDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    // Helper to request wanted permission
    private boolean hasPermission(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission(permission);
                    return false;
                }
            }
        }
        return true;
    }


    public void requestPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainMenu.this, permission) == PackageManager.PERMISSION_GRANTED) {
            } else {
                requestPermissions(new String[]{permission}, 1);
            }
        }
    }


    private void databaseBackupDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.database_backup_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        final CheckBox timedBackupToggle = dialog.findViewById(R.id.timedBackupToggle);
        final Button takeBackupBtn = dialog.findViewById(R.id.takeBackupBtn);
        final Button restoreBackupBtn = dialog.findViewById(R.id.restoreBackupBtn);
        final Button closeBtn = dialog.findViewById(R.id.closeBtn);

        timedBackupToggle.setChecked(sharedPreferences.getBoolean(Constants.SP_TIMED_BACKUP_ENABLED, false));
        timedBackupToggle.setOnCheckedChangeListener((compoundButton, b) -> {
            if (hasPermission(MainMenu.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                SharedPreferences setSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor normalEditor = setSharedPreferences.edit();
                normalEditor.putBoolean(Constants.SP_TIMED_BACKUP_ENABLED, b);
                normalEditor.apply();
                Toast.makeText(this, getString(R.string.timed_back_up_toggle) + " " + (b ? getString(R.string.timed_backup_on) : getString(R.string.timed_backup_off)), Toast.LENGTH_SHORT).show();
            } else {
                timedBackupToggle.setChecked(false);
            }
        });

        takeBackupBtn.setOnClickListener(view -> {
            if (hasPermission(MainMenu.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (databaseHelper.backupDatabase(MainMenu.this)) {
                    genericErrorDialog(getString(R.string.main_menu_result), getString(R.string.main_menu_taking_backup_was_successfull_for_following_directory) + " " +
                            Environment.getExternalStorageDirectory() + "/PaketinSeuranta/Varmuuskopiot/");
                } else {
                    genericErrorDialog(getString(R.string.main_menu_error), getString(R.string.main_menu_taking_backup_failed));
                }
            }
        });
        restoreBackupBtn.setOnClickListener(view -> {
            if (hasPermission(MainMenu.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (databaseHelper.restoreDatabase(MainMenu.this)) {
                    Toast.makeText(MainMenu.this, R.string.main_menu_restore_successfull, Toast.LENGTH_LONG).show();
                    MainMenu.this.finish();
                } else {
                    genericErrorDialog(getString(R.string.main_menu_error), getString(R.string.main_menu_restore_un_successfull));
                }
            }
        });
        closeBtn.setOnClickListener(view -> {
            dialog.dismiss();
        });
    }


    private void checkForAutomaticBackup() {
        if (sharedPreferences.getBoolean(Constants.SP_TIMED_BACKUP_ENABLED, false)) {
            final String lastBackupDate = sharedPreferences.getString(Constants.SP_TIMED_BACKUP_LAST_DATE, null);
            Calendar c = Calendar.getInstance();
            if (lastBackupDate == null) {
                databaseHelper.backupDatabase(this);
                saveBackupDate(c);
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    c.setTime(sdf.parse(lastBackupDate));
                    c.add(Calendar.DATE, 5);
                    if (c.getTimeInMillis() < System.currentTimeMillis()) {
                        databaseHelper.backupDatabase(this);
                        saveBackupDate(c);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveBackupDate(Calendar calendar) {
        Date now = calendar.getTime();
        SimpleDateFormat simpleDate =  new SimpleDateFormat("dd/MM/yyyy");
        String strDt = simpleDate.format(now);
        SharedPreferences setSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor normalEditor = setSharedPreferences.edit();
        normalEditor.putString(Constants.SP_TIMED_BACKUP_LAST_DATE, strDt);
        normalEditor.apply();
        Log.i(TAG, "Backup is taken and date is saved: " + strDt);
    }


    // ---------------------------------------------------------------------------------------------
    // Helpers

    // Generic use error dialog
    private void genericErrorDialog(final String title, final String description) {
        if (!this.isFinishing()) {
            new AlertDialog.Builder(MainMenu.this)
                    .setTitle(title)
                    .setMessage(description)
                    .setPositiveButton(R.string.main_menu_close, (dialog, which) -> {
                    })
                    .setIcon(R.mipmap.ps_logo_round)
                    .show();
        }
    }


    // ---------------------------------------------------------------------------------------------

} // End of class