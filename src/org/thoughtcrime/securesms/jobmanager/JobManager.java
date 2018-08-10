package org.thoughtcrime.securesms.jobmanager;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkStatus;
import androidx.work.impl.WorkDatabase;

public class JobManager {

  private static final Constraints NETWORK_CONSTRAINT = new Constraints.Builder()
                                                                       .setRequiredNetworkType(NetworkType.CONNECTED)
                                                                       .build();

  private final Executor executor = Executors.newSingleThreadExecutor();

  private final Context     context;
  private final WorkManager workManager;

  public JobManager(@NonNull Context context, @NonNull WorkManager workManager) {
    this.context      = context.getApplicationContext();
    this.workManager  = workManager;
  }

  public void add(Job job) {
    executor.execute(() -> {
      Data.Builder dataBuilder = new Data.Builder().putInt(Job.KEY_RETRY_COUNT, getRetryCount(job))
                                                   .putLong(Job.KEY_RETRY_UNTIL, getRetryUntil(job))
                                                   .putBoolean(Job.KEY_REQUIRES_MASTER_SECRET, getRequiresMasterSecret(job))
                                                   .putBoolean(Job.KEY_REQUIRES_SQLCIPHER, getRequiresSqlCipher(job));
      Data data = job.serialize(dataBuilder);

      OneTimeWorkRequest.Builder requestBuilder = new OneTimeWorkRequest.Builder(job.getClass()).setInputData(data);

      if (requiresNetwork(job)) {
        requestBuilder.setConstraints(NETWORK_CONSTRAINT);
      }

      OneTimeWorkRequest request = requestBuilder.build();

      job.onAdded();

      String groupId = getGroupId(job);
      if (groupId != null) {
        workManager.beginUniqueWork(groupId, ExistingWorkPolicy.APPEND, request).enqueue();
      } else {
        workManager.beginWith(request).enqueue();
      }
    });
  }

  public LiveData<WorkStatus> getState(UUID jobId) {
    return workManager.getStatusById(jobId);
  }

  private int getRetryCount(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().getRetryCount();
    }
    return 0;
  }

  private long getRetryUntil(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().getRetryUntil();
    }
    return 0;
  }

  private boolean getRequiresMasterSecret(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().requiresMasterSecret();
    }
    return false;
  }

  private boolean getRequiresSqlCipher(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().requiresSqlCipher();
    }
    return false;
  }

  private boolean requiresNetwork(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().requiresNetwork();
    }
    return false;
  }

  private String getGroupId(Job job) {
    if (job.getJobParameters() != null) {
      return job.getJobParameters().getGroupId();
    }
    return null;
  }

}
