package org.owntracks.android.ui.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;

import org.greenrobot.eventbus.EventBus;
import org.owntracks.android.App;
import org.owntracks.android.BR;
import org.owntracks.android.R;
import org.owntracks.android.injection.modules.android.ActivityModules.BaseActivityModule;
import org.owntracks.android.services.BackgroundService;
import org.owntracks.android.support.DrawerProvider;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.support.RequirementsChecker;
import org.owntracks.android.ui.base.navigator.Navigator;
import org.owntracks.android.ui.base.view.MvvmView;
import org.owntracks.android.ui.base.viewmodel.MvvmViewModel;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.android.support.DaggerAppCompatActivity;

public abstract class BaseActivity<B extends ViewDataBinding, V extends MvvmViewModel> extends DaggerAppCompatActivity {

    protected B binding;
    @Inject
    protected V viewModel;
    @Inject
    protected EventBus eventBus;
    @Inject
    protected DrawerProvider drawerProvider;
    @Inject
    protected Preferences preferences;
    @Inject
    protected RequirementsChecker requirementsChecker;
    @Inject
    protected Navigator navigator;

    @Inject
    @Named(BaseActivityModule.ACTIVITY_FRAGMENT_MANAGER)
    protected FragmentManager fragmentManager;


    private boolean hasEventBus = true;
    private boolean disablesAnimation = false;

    protected void setHasEventBus(boolean enable) {
        this.hasEventBus = enable;
    }

    /* Use this method to set the content view on your Activity. This method also handles
     * creating the binding, setting the view model on the binding and attaching the view. */
    protected final void bindAndAttachContentView(@LayoutRes int layoutResId, @Nullable Bundle savedInstanceState) {
        if (viewModel == null) {
            throw new IllegalStateException("viewModel must not be null and should be injected via activityComponent().inject(this)");
        }
        binding = DataBindingUtil.setContentView(this, layoutResId);
        binding.setVariable(BR.vm, viewModel);
        binding.setLifecycleOwner(this);

        //noinspection unchecked
        viewModel.attachView((MvvmView) this, savedInstanceState);
    }


    private BackgroundService mService;
    private boolean mBound;

    protected boolean isBound() {
        return mBound;
    }

    // Monitors the state of the connection to the service.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service != null) {
                BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service;
                mService = binder.getService();
                mBound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    protected void setSupportToolbar(@NonNull Toolbar toolbar) {
        setSupportToolbar(toolbar, true, true);
    }

    protected void setSupportToolbar(@NonNull Toolbar toolbar, boolean showTitle, boolean showHome) {
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            if (showTitle)
                getSupportActionBar().setTitle(getTitle());

            getSupportActionBar().setDisplayShowTitleEnabled(showTitle);
            getSupportActionBar().setDisplayShowHomeEnabled(showHome);
            getSupportActionBar().setDisplayHomeAsUpEnabled(showHome);
        }

    }

    protected void setSupportToolbarWithDrawer(@NonNull Toolbar toolbar) {
        setSupportToolbar(toolbar, true, true);
        setDrawer(toolbar);
    }


    protected void setDrawer(@NonNull Toolbar toolbar) {
        drawerProvider.attach(toolbar);
    }


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        disablesAnimation = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0;
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) {
            viewModel.saveInstanceState(outState);
        }
    }


    @Override
    public void onStart() {
        if (disablesAnimation)
            overridePendingTransition(0, 0);
        else
            overridePendingTransition(R.anim.push_up_in, R.anim.none);


        super.onStart();

        bindService(new Intent(this, BackgroundService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    protected void onStop() {
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
        super.onStop();
    }


    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        if (viewModel != null) {
            viewModel.detachView();
        }
        binding = null;
        viewModel = null;
    }


    public void onResume() {
        super.onResume();

        if (hasEventBus && !eventBus.isRegistered(viewModel))
            eventBus.register(viewModel);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (eventBus.isRegistered(viewModel))
            eventBus.unregister(viewModel);

        if (disablesAnimation)
            overridePendingTransition(0, 0);
        else
            overridePendingTransition(R.anim.push_up_in, R.anim.none);
    }

    protected final void addFragment(@IdRes int containerViewId, Fragment fragment) {
        fragmentManager.beginTransaction()
                .add(containerViewId, fragment)
                .commit();
    }
}
