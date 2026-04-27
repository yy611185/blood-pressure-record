package com.example.bloodpressurerecord.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity;
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
public final class BloodPressureMeasurementDao_Impl implements BloodPressureMeasurementDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BloodPressureMeasurementEntity> __insertionAdapterOfBloodPressureMeasurementEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public BloodPressureMeasurementDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBloodPressureMeasurementEntity = new EntityInsertionAdapter<BloodPressureMeasurementEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `bp_measurements` (`id`,`memberName`,`systolic`,`diastolic`,`pulse`,`measuredAtMillis`,`level`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BloodPressureMeasurementEntity entity) {
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
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM bp_measurements";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final BloodPressureMeasurementEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBloodPressureMeasurementEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BloodPressureMeasurementEntity>> observeAll() {
    final String _sql = "SELECT * FROM bp_measurements ORDER BY measuredAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"bp_measurements"}, new Callable<List<BloodPressureMeasurementEntity>>() {
      @Override
      @NonNull
      public List<BloodPressureMeasurementEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMemberName = CursorUtil.getColumnIndexOrThrow(_cursor, "memberName");
          final int _cursorIndexOfSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "systolic");
          final int _cursorIndexOfDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "diastolic");
          final int _cursorIndexOfPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "pulse");
          final int _cursorIndexOfMeasuredAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAtMillis");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final List<BloodPressureMeasurementEntity> _result = new ArrayList<BloodPressureMeasurementEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BloodPressureMeasurementEntity _item;
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
            _item = new BloodPressureMeasurementEntity(_tmpId,_tmpMemberName,_tmpSystolic,_tmpDiastolic,_tmpPulse,_tmpMeasuredAtMillis,_tmpLevel);
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
  public Object countAll(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM bp_measurements";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(
      final Continuation<? super List<BloodPressureMeasurementEntity>> $completion) {
    final String _sql = "SELECT * FROM bp_measurements ORDER BY measuredAtMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BloodPressureMeasurementEntity>>() {
      @Override
      @NonNull
      public List<BloodPressureMeasurementEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMemberName = CursorUtil.getColumnIndexOrThrow(_cursor, "memberName");
          final int _cursorIndexOfSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "systolic");
          final int _cursorIndexOfDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "diastolic");
          final int _cursorIndexOfPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "pulse");
          final int _cursorIndexOfMeasuredAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAtMillis");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final List<BloodPressureMeasurementEntity> _result = new ArrayList<BloodPressureMeasurementEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BloodPressureMeasurementEntity _item;
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
            _item = new BloodPressureMeasurementEntity(_tmpId,_tmpMemberName,_tmpSystolic,_tmpDiastolic,_tmpPulse,_tmpMeasuredAtMillis,_tmpLevel);
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

  @Override
  public Object tableExists(final String tableName,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tableName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLegacyBloodPressureRecords(final SupportSQLiteQuery query,
      final Continuation<? super List<LegacyBloodPressureRecordRow>> $completion) {
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LegacyBloodPressureRecordRow>>() {
      @Override
      @NonNull
      public List<LegacyBloodPressureRecordRow> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, query, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndex(_cursor, "id");
          final int _cursorIndexOfMemberName = CursorUtil.getColumnIndex(_cursor, "memberName");
          final int _cursorIndexOfSystolic = CursorUtil.getColumnIndex(_cursor, "systolic");
          final int _cursorIndexOfDiastolic = CursorUtil.getColumnIndex(_cursor, "diastolic");
          final int _cursorIndexOfPulse = CursorUtil.getColumnIndex(_cursor, "pulse");
          final int _cursorIndexOfMeasuredAtMillis = CursorUtil.getColumnIndex(_cursor, "measuredAtMillis");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndex(_cursor, "level");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndex(_cursor, "remark");
          final List<LegacyBloodPressureRecordRow> _result = new ArrayList<LegacyBloodPressureRecordRow>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LegacyBloodPressureRecordRow _item;
            final long _tmpId;
            if (_cursorIndexOfId == -1) {
              _tmpId = 0;
            } else {
              _tmpId = _cursor.getLong(_cursorIndexOfId);
            }
            final String _tmpMemberName;
            if (_cursorIndexOfMemberName == -1) {
              _tmpMemberName = null;
            } else {
              if (_cursor.isNull(_cursorIndexOfMemberName)) {
                _tmpMemberName = null;
              } else {
                _tmpMemberName = _cursor.getString(_cursorIndexOfMemberName);
              }
            }
            final int _tmpSystolic;
            if (_cursorIndexOfSystolic == -1) {
              _tmpSystolic = 0;
            } else {
              _tmpSystolic = _cursor.getInt(_cursorIndexOfSystolic);
            }
            final int _tmpDiastolic;
            if (_cursorIndexOfDiastolic == -1) {
              _tmpDiastolic = 0;
            } else {
              _tmpDiastolic = _cursor.getInt(_cursorIndexOfDiastolic);
            }
            final Integer _tmpPulse;
            if (_cursorIndexOfPulse == -1) {
              _tmpPulse = null;
            } else {
              if (_cursor.isNull(_cursorIndexOfPulse)) {
                _tmpPulse = null;
              } else {
                _tmpPulse = _cursor.getInt(_cursorIndexOfPulse);
              }
            }
            final long _tmpMeasuredAtMillis;
            if (_cursorIndexOfMeasuredAtMillis == -1) {
              _tmpMeasuredAtMillis = 0;
            } else {
              _tmpMeasuredAtMillis = _cursor.getLong(_cursorIndexOfMeasuredAtMillis);
            }
            final String _tmpLevel;
            if (_cursorIndexOfLevel == -1) {
              _tmpLevel = null;
            } else {
              if (_cursor.isNull(_cursorIndexOfLevel)) {
                _tmpLevel = null;
              } else {
                _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
              }
            }
            final String _tmpRemark;
            if (_cursorIndexOfRemark == -1) {
              _tmpRemark = null;
            } else {
              if (_cursor.isNull(_cursorIndexOfRemark)) {
                _tmpRemark = null;
              } else {
                _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
              }
            }
            _item = new LegacyBloodPressureRecordRow(_tmpId,_tmpMemberName,_tmpSystolic,_tmpDiastolic,_tmpPulse,_tmpMeasuredAtMillis,_tmpLevel,_tmpRemark);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
