package com.sleeppulse.app.data.repository;

import com.sleeppulse.app.data.local.NightlySummaryDao;
import com.sleeppulse.app.data.source.SensorDataSource;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SleepRepositoryImpl_Factory implements Factory<SleepRepositoryImpl> {
  private final Provider<SensorDataSource> sensorDataSourceProvider;

  private final Provider<NightlySummaryDao> daoProvider;

  public SleepRepositoryImpl_Factory(Provider<SensorDataSource> sensorDataSourceProvider,
      Provider<NightlySummaryDao> daoProvider) {
    this.sensorDataSourceProvider = sensorDataSourceProvider;
    this.daoProvider = daoProvider;
  }

  @Override
  public SleepRepositoryImpl get() {
    return newInstance(sensorDataSourceProvider.get(), daoProvider.get());
  }

  public static SleepRepositoryImpl_Factory create(
      Provider<SensorDataSource> sensorDataSourceProvider,
      Provider<NightlySummaryDao> daoProvider) {
    return new SleepRepositoryImpl_Factory(sensorDataSourceProvider, daoProvider);
  }

  public static SleepRepositoryImpl newInstance(SensorDataSource sensorDataSource,
      NightlySummaryDao dao) {
    return new SleepRepositoryImpl(sensorDataSource, dao);
  }
}
