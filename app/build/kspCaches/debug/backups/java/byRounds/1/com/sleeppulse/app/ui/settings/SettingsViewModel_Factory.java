package com.sleeppulse.app.ui.settings;

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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  @Override
  public SettingsViewModel get() {
    return newInstance();
  }

  public static SettingsViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SettingsViewModel newInstance() {
    return new SettingsViewModel();
  }

  private static final class InstanceHolder {
    private static final SettingsViewModel_Factory INSTANCE = new SettingsViewModel_Factory();
  }
}
