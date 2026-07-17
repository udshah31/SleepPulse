package com.sleeppulse.app.data.source;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class BleSensorDataSource_Factory implements Factory<BleSensorDataSource> {
  private final Provider<Context> contextProvider;

  public BleSensorDataSource_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BleSensorDataSource get() {
    return newInstance(contextProvider.get());
  }

  public static BleSensorDataSource_Factory create(Provider<Context> contextProvider) {
    return new BleSensorDataSource_Factory(contextProvider);
  }

  public static BleSensorDataSource newInstance(Context context) {
    return new BleSensorDataSource(context);
  }
}
