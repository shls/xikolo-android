package de.xikolo.controller.course;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import de.xikolo.R;
import de.xikolo.controller.BaseFragment;
import de.xikolo.controller.ModuleActivity;
import de.xikolo.controller.course.adapter.ItemListAdapter;
import de.xikolo.controller.course.adapter.ModuleListAdapter;
import de.xikolo.controller.helper.NotificationController;
import de.xikolo.controller.helper.RefeshLayoutController;
import de.xikolo.data.entities.Course;
import de.xikolo.data.entities.Item;
import de.xikolo.data.entities.Module;
import de.xikolo.model.ModuleModel;
import de.xikolo.model.Result;
import de.xikolo.util.NetworkUtil;
import de.xikolo.util.ToastUtil;
import de.xikolo.view.SpaceItemDecoration;

public class CourseLearningsFragment extends BaseFragment implements SwipeRefreshLayout.OnRefreshListener,
        ModuleListAdapter.OnModuleButtonClickListener, ItemListAdapter.OnItemButtonClickListener {

    public final static String TAG = CourseLearningsFragment.class.getSimpleName();

    private static final String ARG_COURSE = "arg_course";
    private static final String ARG_MODULES = "arg_modules";

    private static final int REQUEST_CODE_MODULES = 1;

    private SwipeRefreshLayout refreshLayout;

    private ModuleModel moduleModel;
    private ModuleListAdapter adapter;

    private NotificationController notificationController;

    private Course course;
    private List<Module> modules;

    public CourseLearningsFragment() {
        // Required empty public constructor
    }

    public static CourseLearningsFragment newInstance(Course course) {
        CourseLearningsFragment fragment = new CourseLearningsFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_COURSE, course);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (modules != null) {
            outState.putParcelableArrayList(ARG_MODULES, (ArrayList<Module>) modules);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            course = getArguments().getParcelable(ARG_COURSE);
        }
        if (savedInstanceState != null) {
            modules = savedInstanceState.getParcelableArrayList(ARG_MODULES);
        }
        setHasOptionsMenu(true);

        moduleModel = new ModuleModel(jobManager);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_learnings, container, false);

        refreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.refreshLayout);
        RefeshLayoutController.setup(refreshLayout, this);

        RecyclerView recyclerView = (RecyclerView) layout.findViewById(R.id.recyclerView);
        adapter = new ModuleListAdapter(getActivity(), course, this, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new SpaceItemDecoration(
                0,
                getActivity().getResources().getDimensionPixelSize(R.dimen.card_vertical_margin),
                false,
                new SpaceItemDecoration.RecyclerViewInfo() {
                    @Override
                    public boolean isHeader(int position) {
                        return false;
                    }

                    @Override
                    public int getSpanCount() {
                        return 1;
                    }

                    @Override
                    public int getItemCount() {
                        return adapter.getItemCount();
                    }
                }
        ));

        notificationController = new NotificationController(layout);

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (modules == null) {
            notificationController.setProgressVisible(true);
            requestModulesWithItems(false, false);
        } else {
            adapter.updateModules(modules);
        }
    }

    @Override
    public void onRefresh() {
        requestModulesWithItems(true, false);
    }

    private void requestModulesWithItems(final boolean userRequest, final boolean includeProgress) {
        Result<List<Module>> result = new Result<List<Module>>() {
            @Override
            protected void onSuccess(List<Module> result, DataSource dataSource) {
                if (result.size() > 0) {
                    notificationController.setInvisible();
                }
                if (!NetworkUtil.isOnline(getActivity()) && dataSource.equals(DataSource.LOCAL) ||
                        dataSource.equals(DataSource.NETWORK)) {
                    refreshLayout.setRefreshing(false);
                }

                modules = result;

                if (!NetworkUtil.isOnline(getActivity()) && dataSource.equals(DataSource.LOCAL) && result.size() == 0) {
                    adapter.clear();
                    notificationController.setTitle(R.string.notification_no_network);
                    notificationController.setSummary(R.string.notification_no_network_with_offline_mode_summary);
                    notificationController.setNotificationVisible(true);
                } else {
                    adapter.updateModules(modules);
                }
            }

            @Override
            protected void onWarning(WarnCode warnCode) {
                if (warnCode == WarnCode.NO_NETWORK && userRequest) {
                    NetworkUtil.showNoConnectionToast();
                }
            }

            @Override
            protected void onError(ErrorCode errorCode) {
                ToastUtil.show(R.string.error);
                refreshLayout.setRefreshing(false);
                notificationController.setInvisible();
            }
        };

        if (!notificationController.isProgressVisible()) {
            refreshLayout.setRefreshing(true);
        }

        moduleModel.getModulesWithItems(result, course, includeProgress);
    }

    @Override
    public void onModuleButtonClicked(Course course, Module module) {
        startModuleActivity(course, module, null);
    }

    @Override
    public void onItemButtonClicked(Course course, Module module, Item item) {
        startModuleActivity(course, module, item);
    }

    private void startModuleActivity(Course course, Module module, Item item) {
        Intent intent = new Intent(getActivity(), ModuleActivity.class);
        Bundle b = new Bundle();
        b.putParcelable(ModuleActivity.ARG_COURSE, course);
        b.putParcelable(ModuleActivity.ARG_MODULE, module);
        b.putParcelable(ModuleActivity.ARG_ITEM, item);
        intent.putExtras(b);
        getActivity().startActivityForResult(intent, REQUEST_CODE_MODULES);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_MODULES && resultCode == Activity.RESULT_OK) {
            Module newModule = data.getExtras().getParcelable(ModuleActivity.ARG_MODULE);

            if (modules != null && adapter != null) {
                Module oldModule = modules.get(modules.indexOf(newModule));
                if (newModule != null) {
                    for (Item newItem : newModule.items) {
                        for (Item oldItem : oldModule.items) {
                            if (oldItem.equals(newItem)) {
                                oldItem.progress = newItem.progress;
                            }
                        }
                    }
                }
                adapter.updateModules(modules);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.refresh, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.action_refresh:
                onRefresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
