package com.example.bloodpressurerecord.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class BloodPressureRecordDao_Impl implements BloodPressureRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BloodPressureRecordEntity> __insertionAdapterOfBloodPressureRecordEntity;

  public BloodPressureRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBloodPressureRecordEntity = new EntityInsertionAdapter<BloodPressureRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `blood_pressure_records` (`id`,`memberName`,`systolic`,`diastolic`,`pulse`,`measuredAtMillis`,`level`,`remark`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BloodPressureRecordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMemberName());
        statement.bindLong(3, entity.getSystolic());
        statement.bindLong(4, entity.getDiastolic());
        if (entity.getPulse() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getPulse());
        }
        statement.bindLong(6, entity.getMeasuredAtMillis());
        statement.bindString(7, entity.getLevel());
        statement.bindString(8, entity.getRemark());
      }
    };
  }

  @Override
  public Object insert(final BloodPressureRecordEntity record,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBloodPressureRecordEntity.insert(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BloodPressureRecordEntity>> observeAll() {
    final String _sql = "SELECT * FROM blood_pressure_records ORDER BY measuredAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"blood_pressure_records"}, new Callable<List<BloodPressureRecordEntity>>() {
      @Override
      @NonNull
      public List<BloodPressureRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMemberName = CursorUtil.getColumnIndexOrThrow(_cursor, "memberName");
          final int _cursorIndexOfSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "systolic");
          final int _cursorIndexOfDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "diastolic");
          final int _cursorIndexOfPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "pulse");
          final int _cursorIndexOfMeasuredAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAtMillis");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final List<BloodPressureRecordEntity> _result = new ArrayList<BloodPressureRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BloodPressureRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMemberName;
            _tmpMemberName = _cursor.getString(_cursorIndexOfMemberName);
            final int _tmpSystolic;
            _tmpSystolic = _cursor.getInt(_cursorIndexOfSystolic);
            final int _tmpDiastolic;
            _tmpDiastolic = _cursor.getInt(_cursorIndexOfDiastolic);
            final Integer _tmpPulse;
            if (_cursor.isNull(_cursorIndexOfPulse)) {
              _tmpPulse = null;
            } else {
              _tmpPulse = _cursor.getInt(_cursorIndexOfPulse);
            }
            final long _tmpMeasuredAtMillis;
            _tmpMeasuredAtMillis = _cursor.getLong(_cursorIndexOfMeasuredAtMillis);
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final String _tmpRemark;
            _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            _item = new BloodPressureRecordEntity(_tmpId,_tmpMemberName,_tmpSystolic,_tmpDiastolic,_tmpPulse,_tmpMeasuredAtMillis,_tmpLevel,_tmpRemark);
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

  @Override
  public Object getAll(final Continuation<? super List<BloodPressureRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM blood_pressure_records ORDER BY measuredAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BloodPressureRecordEntity>>() {
      @Override
      @NonNull
      public List<BloodPressureRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMemberName = CursorUtil.getColumnIndexOrThrow(_cursor, "memberName");
          final int _cursorIndexOfSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "systolic");
          final int _cursorIndexOfDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "diastolic");
          final int _cursorIndexOfPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "pulse");
          final int _cursorIndexOfMeasuredAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAtMillis");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final List<BloodPressureRecordEntity> _result = new ArrayList<BloodPressureRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BloodPressureRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMemberName;
            _tmpMemberName = _cursor.getString(_cursorIndexOfMemberName);
            final int _tmpSystolic;
            _tmpSystolic = _cursor.getInt(_cursorIndexOfSystolic);
            final int _tmpDiastolic;
            _tmpDiastolic = _cursor.getInt(_cursorIndexOfDiastolic);
            final Integer _tmpPulse;
            if (_cursor.isNull(_cursorIndexOfPulse)) {
              _tmpPulse = null;
            } else {
              _tmpPulse = _cursor.getInt(_cursorIndexOfPulse);
            }
            final long _tmpMeasuredAtMillis;
            _tmpMeasuredAtMillis = _cursor.getLong(_cursorIndexOfMeasuredAtMillis);
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final String _tmpRemark;
            _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            _item = new BloodPressureRecordEntity(_tmpId,_tmpMemberName,_tmpSystolic,_tmpDiastolic,_tmpPulse,_tmpMeasuredAtMillis,_tmpLevel,_tmpRemark);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
