package de.xikolo.controller.course;

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
import de.xikolo.controller.course.adapter.ProgressListAdapter;
import de.xikolo.controller.helper.NotificationController;
import de.xikolo.controller.helper.RefeshLayoutController;
import de.xikolo.data.entities.Course;
import de.xikolo.data.entities.Module;
import de.xikolo.model.ModuleModel;
import de.xikolo.model.Result;
import de.xikolo.util.NetworkUtil;
import de.xikolo.util.ToastUtil;
import de.xikolo.view.SpaceItemDecoration;

public class ProgressFragment extends BaseFragment implements SwipeRefreshLayout.OnRefreshListener {

    public static final String TAG = ProgressFragment.class.getSimpleName();

    private static final String ARG_COURSE = "arg_course";
    private static final String ARG_MODULES = "arg_modules";

    private Course course;
    private List<Module> modules;

    private ModuleModel moduleModel;

    private ProgressListAdapter adapter;

    private SwipeRefreshLayout refreshLayout;

    private NotificationController notificationController;

    public ProgressFragment() {
        // Required empty public constructor
    }

    public static ProgressFragment newInstance(Course course) {
        ProgressFragment fragment = new ProgressFragment();
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
        View layout = inflater.inflate(R.layout.fragment_progress, container, false);

        RecyclerView recyclerView = (RecyclerView) layout.findViewById(R.id.recyclerView);

        adapter = new ProgressListAdapter(getActivity());

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

        refreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.refreshLayout);
        RefeshLayoutController.setup(refreshLayout, this);

        notificationController = new NotificationController(layout);

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (modules == null) {
            notificationController.setProgressVisible(true);
            requestProgress(false);
        } else {
            adapter.updateModules(modules);
        }
    }

    private void requestProgress(final boolean userRequest) {
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
                    refreshLayout.setRefreshing(false);
                    notificationController.setTitle(R.string.notification_no_network);
                    notificationController.setSummary(R.string.notification_no_network_with_offline_mode_summary);
                    notificationController.setNotificationVisible(true);
                } else if (modules != null && modules.size() > 0) {
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
                notificationController.setInvisible();
                refreshLayout.setRefreshing(false);
                ToastUtil.show(R.string.error);
            }
        };

        if (!notificationController.isProgressVisible()) {
            refreshLayout.setRefreshing(true);
        }

        moduleModel.getModules(result, course, true);
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

    @Override
    public void onRefresh() {
        refreshLayout.setRefreshing(true);
        requestProgress(true);
    }

}
