package com.sleeppulse.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SleepPulseDatabase_Impl extends SleepPulseDatabase {
  private volatile NightlySummaryDao _nightlySummaryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `nightly_summary` (`dateEpochDay` INTEGER NOT NULL, `sleepScore` INTEGER NOT NULL, `avgHeartRateBpm` INTEGER NOT NULL, `avgHrvMillis` REAL NOT NULL, `totalSleepMinutes` INTEGER NOT NULL, `deepSleepMinutes` INTEGER NOT NULL, `remSleepMinutes` INTEGER NOT NULL, PRIMARY KEY(`dateEpochDay`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b43fe45c97fe708bd643eecf7e05bd31')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `nightly_summary`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsNightlySummary = new HashMap<String, TableInfo.Column>(7);
        _columnsNightlySummary.put("dateEpochDay", new TableInfo.Column("dateEpochDay", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("sleepScore", new TableInfo.Column("sleepScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("avgHeartRateBpm", new TableInfo.Column("avgHeartRateBpm", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("avgHrvMillis", new TableInfo.Column("avgHrvMillis", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("totalSleepMinutes", new TableInfo.Column("totalSleepMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("deepSleepMinutes", new TableInfo.Column("deepSleepMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNightlySummary.put("remSleepMinutes", new TableInfo.Column("remSleepMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNightlySummary = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesNightlySummary = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoNightlySummary = new TableInfo("nightly_summary", _columnsNightlySummary, _foreignKeysNightlySummary, _indicesNightlySummary);
        final TableInfo _existingNightlySummary = TableInfo.read(db, "nightly_summary");
        if (!_infoNightlySummary.equals(_existingNightlySummary)) {
          return new RoomOpenHelper.ValidationResult(false, "nightly_summary(com.sleeppulse.app.data.local.NightlySummaryEntity).\n"
                  + " Expected:\n" + _infoNightlySummary + "\n"
                  + " Found:\n" + _existingNightlySummary);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b43fe45c97fe708bd643eecf7e05bd31", "8d12bdee11a30e53ff0f36922a1494f9");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "nightly_summary");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `nightly_summary`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(NightlySummaryDao.class, NightlySummaryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public NightlySummaryDao nightlySummaryDao() {
    if (_nightlySummaryDao != null) {
      return _nightlySummaryDao;
    } else {
      synchronized(this) {
        if(_nightlySummaryDao == null) {
          _nightlySummaryDao = new NightlySummaryDao_Impl(this);
        }
        return _nightlySummaryDao;
      }
    }
  }
}
