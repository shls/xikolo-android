package de.xikolo.model.jobs;

import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.RetryConstraint;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.xikolo.GlobalApplication;
import de.xikolo.data.database.ModuleDataAccess;
import de.xikolo.data.entities.Course;
import de.xikolo.data.entities.Module;
import de.xikolo.data.net.JsonRequest;
import de.xikolo.model.Result;
import de.xikolo.model.UserModel;
import de.xikolo.util.Config;
import de.xikolo.util.NetworkUtil;

public class RetrieveModulesJob extends Job {

    public static final String TAG = RetrieveModulesJob.class.getSimpleName();

    private static final AtomicInteger jobCounter = new AtomicInteger(0);

    private final int id;

    private Result<List<Module>> result;
    private Course course;
    private boolean includeProgress;

    public RetrieveModulesJob(Result<List<Module>> result, Course course, boolean includeProgress) {
        super(new Params(Priority.MID));
        id = jobCounter.incrementAndGet();

        this.result = result;
        this.course = course;
        this.includeProgress = includeProgress;
    }

    @Override
    public void onAdded() {
        if (Config.DEBUG) Log.i(TAG, TAG + " added | includeProgress " + includeProgress + " | course.id " + course.id);
    }

    @Override
    public void onRun() throws Throwable {
        if (!UserModel.isLoggedIn(GlobalApplication.getInstance()) || !course.is_enrolled) {
            result.error(Result.ErrorCode.NO_AUTH);
        } else {
            ModuleDataAccess moduleDataAccess = GlobalApplication.getInstance()
                    .getDataAccessFactory().getModuleDataAccess();
            List<Module> localModules = moduleDataAccess.getAllModulesForCourse(course);
            if (includeProgress) {
                List<Module> deleteList = new ArrayList<>();
                for (Module module : localModules) {
                    if (module.progress == null) {
                        deleteList.add(module);
                    }
                }
                localModules.removeAll(deleteList);
            }
            result.success(localModules, Result.DataSource.LOCAL);

            if (NetworkUtil.isOnline(GlobalApplication.getInstance())) {
                Type type = new TypeToken<List<Module>>(){}.getType();

                String url = Config.API + Config.COURSES + course.id + "/"
                        + Config.MODULES + "?include_progress=" + includeProgress;

                JsonRequest request = new JsonRequest(url, type);
                request.setCache(false);

                request.setToken(UserModel.getToken(GlobalApplication.getInstance()));

                Object o = request.getResponse();
                if (o != null) {
                    @SuppressWarnings("unchecked")
                    List<Module> modules = (List<Module>) o;
                    if (Config.DEBUG) Log.i(TAG, "Modules received (" + modules.size() + ")");

                    for (Module module : modules) {
                        moduleDataAccess.addOrUpdateModule(course, module, includeProgress);
                    }

                    result.success(modules, Result.DataSource.NETWORK);
                } else {
                    if (Config.DEBUG) Log.w(TAG, "No Modules received");
                   result.error(Result.ErrorCode.NO_RESULT);
                }
            } else {
                result.warn(Result.WarnCode.NO_NETWORK);
            }
        }

    }

    @Override
    protected void onCancel() {
        result.error(Result.ErrorCode.ERROR);
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
        return RetryConstraint.CANCEL;
    }

}
