package com.sleeppulse.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NightlySummaryDao_Impl implements NightlySummaryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NightlySummaryEntity> __insertionAdapterOfNightlySummaryEntity;

  private final SharedSQLiteStatement __preparedStmtOfTrimToLast30Days;

  public NightlySummaryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNightlySummaryEntity = new EntityInsertionAdapter<NightlySummaryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `nightly_summary` (`dateEpochDay`,`sleepScore`,`avgHeartRateBpm`,`avgHrvMillis`,`totalSleepMinutes`,`deepSleepMinutes`,`remSleepMinutes`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NightlySummaryEntity entity) {
        statement.bindLong(1, entity.getDateEpochDay());
        statement.bindLong(2, entity.getSleepScore());
        statement.bindLong(3, entity.getAvgHeartRateBpm());
        statement.bindDouble(4, entity.getAvgHrvMillis());
        statement.bindLong(5, entity.getTotalSleepMinutes());
        statement.bindLong(6, entity.getDeepSleepMinutes());
        statement.bindLong(7, entity.getRemSleepMinutes());
      }
    };
    this.__preparedStmtOfTrimToLast30Days = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM nightly_summary WHERE dateEpochDay NOT IN (SELECT dateEpochDay FROM nightly_summary ORDER BY dateEpochDay DESC LIMIT 30)";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final NightlySummaryEntity summary,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNightlySummaryEntity.insert(summary);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object trimToLast30Days(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfTrimToLast30Days.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfTrimToLast30Days.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<NightlySummaryEntity>> observeRecent() {
    final String _sql = "SELECT * FROM nightly_summary ORDER BY dateEpochDay DESC LIMIT 30";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"nightly_summary"}, new Callable<List<NightlySummaryEntity>>() {
      @Override
      @NonNull
      public List<NightlySummaryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDateEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dateEpochDay");
          final int _cursorIndexOfSleepScore = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepScore");
          final int _cursorIndexOfAvgHeartRateBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "avgHeartRateBpm");
          final int _cursorIndexOfAvgHrvMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "avgHrvMillis");
          final int _cursorIndexOfTotalSleepMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalSleepMinutes");
          final int _cursorIndexOfDeepSleepMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepMinutes");
          final int _cursorIndexOfRemSleepMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "remSleepMinutes");
          final List<NightlySummaryEntity> _result = new ArrayList<NightlySummaryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NightlySummaryEntity _item;
            final long _tmpDateEpochDay;
            _tmpDateEpochDay = _cursor.getLong(_cursorIndexOfDateEpochDay);
            final int _tmpSleepScore;
            _tmpSleepScore = _cursor.getInt(_cursorIndexOfSleepScore);
            final int _tmpAvgHeartRateBpm;
            _tmpAvgHeartRateBpm = _cursor.getInt(_cursorIndexOfAvgHeartRateBpm);
            final double _tmpAvgHrvMillis;
            _tmpAvgHrvMillis = _cursor.getDouble(_cursorIndexOfAvgHrvMillis);
            final int _tmpTotalSleepMinutes;
            _tmpTotalSleepMinutes = _cursor.getInt(_cursorIndexOfTotalSleepMinutes);
            final int _tmpDeepSleepMinutes;
            _tmpDeepSleepMinutes = _cursor.getInt(_cursorIndexOfDeepSleepMinutes);
            final int _tmpRemSleepMinutes;
            _tmpRemSleepMinutes = _cursor.getInt(_cursorIndexOfRemSleepMinutes);
            _item = new NightlySummaryEntity(_tmpDateEpochDay,_tmpSleepScore,_tmpAvgHeartRateBpm,_tmpAvgHrvMillis,_tmpTotalSleepMinutes,_tmpDeepSleepMinutes,_tmpRemSleepMinutes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
