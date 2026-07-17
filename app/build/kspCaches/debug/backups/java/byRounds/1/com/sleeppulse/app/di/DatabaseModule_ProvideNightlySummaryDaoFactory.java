package com.sleeppulse.app.di;

import com.sleeppulse.app.data.local.NightlySummaryDao;
import com.sleeppulse.app.data.local.SleepPulseDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideNightlySummaryDaoFactory implements Factory<NightlySummaryDao> {
  private final Provider<SleepPulseDatabase> databaseProvider;

  public DatabaseModule_ProvideNightlySummaryDaoFactory(
      Provider<SleepPulseDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public NightlySummaryDao get() {
    return provideNightlySummaryDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideNightlySummaryDaoFactory create(
      Provider<SleepPulseDatabase> databaseProvider) {
    return new DatabaseModule_ProvideNightlySummaryDaoFactory(databaseProvider);
  }

  public static NightlySummaryDao provideNightlySummaryDao(SleepPulseDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideNightlySummaryDao(database));
  }
}
