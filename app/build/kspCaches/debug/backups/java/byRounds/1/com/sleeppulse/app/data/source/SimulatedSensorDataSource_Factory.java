package com.sleeppulse.app.data.source;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class SimulatedSensorDataSource_Factory implements Factory<SimulatedSensorDataSource> {
  @Override
  public SimulatedSensorDataSource get() {
    return newInstance();
  }

  public static SimulatedSensorDataSource_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SimulatedSensorDataSource newInstance() {
    return new SimulatedSensorDataSource();
  }

  private static final class InstanceHolder {
    private static final SimulatedSensorDataSource_Factory INSTANCE = new SimulatedSensorDataSource_Factory();
  }
}
