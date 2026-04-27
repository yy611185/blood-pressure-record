package com.example.bloodpressurerecord.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity;
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity;
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionWithReadings;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MeasurementSessionDao_Impl implements MeasurementSessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MeasurementSessionEntity> __insertionAdapterOfMeasurementSessionEntity;

  private final EntityInsertionAdapter<MeasurementReadingEntity> __insertionAdapterOfMeasurementReadingEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReadingsBySessionId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSessionById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllReadings;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllSessions;

  public MeasurementSessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMeasurementSessionEntity = new EntityInsertionAdapter<MeasurementSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `measurement_sessions` (`id`,`measuredAt`,`scene`,`note`,`symptomsJson`,`avgSystolic`,`avgDiastolic`,`avgPulse`,`category`,`highRiskAlertTriggered`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MeasurementSessionEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindLong(2, entity.getMeasuredAt());
        statement.bindString(3, entity.getScene());
        if (entity.getNote() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getNote());
        }
        if (entity.getSymptomsJson() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSymptomsJson());
        }
        statement.bindLong(6, entity.getAvgSystolic());
        statement.bindLong(7, entity.getAvgDiastolic());
        if (entity.getAvgPulse() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getAvgPulse());
        }
        statement.bindString(9, entity.getCategory());
        final int _tmp = entity.getHighRiskAlertTriggered() ? 1 : 0;
        statement.bindLong(10, _tmp);
        statement.bindLong(11, entity.getCreatedAt());
        statement.bindLong(12, entity.getUpdatedAt());
      }
    };
    this.__insertionAdapterOfMeasurementReadingEntity = new EntityInsertionAdapter<MeasurementReadingEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `measurement_readings` (`id`,`sessionId`,`orderIndex`,`systolic`,`diastolic`,`pulse`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MeasurementReadingEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindLong(3, entity.getOrderIndex());
        statement.bindLong(4, entity.getSystolic());
        statement.bindLong(5, entity.getDiastolic());
        if (entity.getPulse() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getPulse());
        }
      }
    };
    this.__preparedStmtOfDeleteReadingsBySessionId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM measurement_readings WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSessionById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM measurement_sessions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllReadings = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM measurement_readings";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllSessions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM measurement_sessions";
        return _query;
      }
    };
  }

  @Override
  public Object insertSession(final MeasurementSessionEntity session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMeasurementSessionEntity.insert(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertReadings(final List<MeasurementReadingEntity> readings,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMeasurementReadingEntity.insert(readings);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSessionWithReadings(final MeasurementSessionEntity session,
      final List<MeasurementReadingEntity> readings, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> MeasurementSessionDao.DefaultImpls.insertSessionWithReadings(MeasurementSessionDao_Impl.this, session, readings, __cont), $completion);
  }

  @Override
  public Object updateSessionWithReadings(final MeasurementSessionEntity session,
      final List<MeasurementReadingEntity> readings, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> MeasurementSessionDao.DefaultImpls.updateSessionWithReadings(MeasurementSessionDao_Impl.this, session, readings, __cont), $completion);
  }

  @Override
  public Object deleteReadingsBySessionId(final String sessionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteReadingsBySessionId.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sessionId);
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
          __preparedStmtOfDeleteReadingsBySessionId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSessionById(final String sessionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSessionById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sessionId);
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
          __preparedStmtOfDeleteSessionById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllReadings(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllReadings.acquire();
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
          __preparedStmtOfDeleteAllReadings.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllSessions(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllSessions.acquire();
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
          __preparedStmtOfDeleteAllSessions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getSessionWithReadings(final String sessionId,
      final Continuation<? super MeasurementSessionWithReadings> $completion) {
    final String _sql = "SELECT * FROM measurement_sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, true, _cancellationSignal, new Callable<MeasurementSessionWithReadings>() {
      @Override
      @Nullable
      public MeasurementSessionWithReadings call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfMeasuredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAt");
            final int _cursorIndexOfScene = CursorUtil.getColumnIndexOrThrow(_cursor, "scene");
            final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
            final int _cursorIndexOfSymptomsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomsJson");
            final int _cursorIndexOfAvgSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgSystolic");
            final int _cursorIndexOfAvgDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgDiastolic");
            final int _cursorIndexOfAvgPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "avgPulse");
            final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
            final int _cursorIndexOfHighRiskAlertTriggered = CursorUtil.getColumnIndexOrThrow(_cursor, "highRiskAlertTriggered");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<MeasurementReadingEntity>> _collectionReadings = new ArrayMap<String, ArrayList<MeasurementReadingEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfId);
              if (!_collectionReadings.containsKey(_tmpKey)) {
                _collectionReadings.put(_tmpKey, new ArrayList<MeasurementReadingEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(_collectionReadings);
            final MeasurementSessionWithReadings _result;
            if (_cursor.moveToFirst()) {
              final MeasurementSessionEntity _tmpSession;
              final String _tmpId;
              _tmpId = _cursor.getString(_cursorIndexOfId);
              final long _tmpMeasuredAt;
              _tmpMeasuredAt = _cursor.getLong(_cursorIndexOfMeasuredAt);
              final String _tmpScene;
              _tmpScene = _cursor.getString(_cursorIndexOfScene);
              final String _tmpNote;
              if (_cursor.isNull(_cursorIndexOfNote)) {
                _tmpNote = null;
              } else {
                _tmpNote = _cursor.getString(_cursorIndexOfNote);
              }
              final String _tmpSymptomsJson;
              if (_cursor.isNull(_cursorIndexOfSymptomsJson)) {
                _tmpSymptomsJson = null;
              } else {
                _tmpSymptomsJson = _cursor.getString(_cursorIndexOfSymptomsJson);
              }
              final int _tmpAvgSystolic;
              _tmpAvgSystolic = _cursor.getInt(_cursorIndexOfAvgSystolic);
              final int _tmpAvgDiastolic;
              _tmpAvgDiastolic = _cursor.getInt(_cursorIndexOfAvgDiastolic);
              final Integer _tmpAvgPulse;
              if (_cursor.isNull(_cursorIndexOfAvgPulse)) {
                _tmpAvgPulse = null;
              } else {
                _tmpAvgPulse = _cursor.getInt(_cursorIndexOfAvgPulse);
              }
              final String _tmpCategory;
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
              final boolean _tmpHighRiskAlertTriggered;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfHighRiskAlertTriggered);
              _tmpHighRiskAlertTriggered = _tmp != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpSession = new MeasurementSessionEntity(_tmpId,_tmpMeasuredAt,_tmpScene,_tmpNote,_tmpSymptomsJson,_tmpAvgSystolic,_tmpAvgDiastolic,_tmpAvgPulse,_tmpCategory,_tmpHighRiskAlertTriggered,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<MeasurementReadingEntity> _tmpReadingsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfId);
              _tmpReadingsCollection = _collectionReadings.get(_tmpKey_1);
              _result = new MeasurementSessionWithReadings(_tmpSession,_tmpReadingsCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
            _statement.release();
          }
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<MeasurementSessionWithReadings> observeSessionWithReadings(final String sessionId) {
    final String _sql = "SELECT * FROM measurement_sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"measurement_readings",
        "measurement_sessions"}, new Callable<MeasurementSessionWithReadings>() {
      @Override
      @Nullable
      public MeasurementSessionWithReadings call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfMeasuredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAt");
            final int _cursorIndexOfScene = CursorUtil.getColumnIndexOrThrow(_cursor, "scene");
            final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
            final int _cursorIndexOfSymptomsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomsJson");
            final int _cursorIndexOfAvgSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgSystolic");
            final int _cursorIndexOfAvgDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgDiastolic");
            final int _cursorIndexOfAvgPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "avgPulse");
            final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
            final int _cursorIndexOfHighRiskAlertTriggered = CursorUtil.getColumnIndexOrThrow(_cursor, "highRiskAlertTriggered");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<MeasurementReadingEntity>> _collectionReadings = new ArrayMap<String, ArrayList<MeasurementReadingEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfId);
              if (!_collectionReadings.containsKey(_tmpKey)) {
                _collectionReadings.put(_tmpKey, new ArrayList<MeasurementReadingEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(_collectionReadings);
            final MeasurementSessionWithReadings _result;
            if (_cursor.moveToFirst()) {
              final MeasurementSessionEntity _tmpSession;
              final String _tmpId;
              _tmpId = _cursor.getString(_cursorIndexOfId);
              final long _tmpMeasuredAt;
              _tmpMeasuredAt = _cursor.getLong(_cursorIndexOfMeasuredAt);
              final String _tmpScene;
              _tmpScene = _cursor.getString(_cursorIndexOfScene);
              final String _tmpNote;
              if (_cursor.isNull(_cursorIndexOfNote)) {
                _tmpNote = null;
              } else {
                _tmpNote = _cursor.getString(_cursorIndexOfNote);
              }
              final String _tmpSymptomsJson;
              if (_cursor.isNull(_cursorIndexOfSymptomsJson)) {
                _tmpSymptomsJson = null;
              } else {
                _tmpSymptomsJson = _cursor.getString(_cursorIndexOfSymptomsJson);
              }
              final int _tmpAvgSystolic;
              _tmpAvgSystolic = _cursor.getInt(_cursorIndexOfAvgSystolic);
              final int _tmpAvgDiastolic;
              _tmpAvgDiastolic = _cursor.getInt(_cursorIndexOfAvgDiastolic);
              final Integer _tmpAvgPulse;
              if (_cursor.isNull(_cursorIndexOfAvgPulse)) {
                _tmpAvgPulse = null;
              } else {
                _tmpAvgPulse = _cursor.getInt(_cursorIndexOfAvgPulse);
              }
              final String _tmpCategory;
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
              final boolean _tmpHighRiskAlertTriggered;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfHighRiskAlertTriggered);
              _tmpHighRiskAlertTriggered = _tmp != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpSession = new MeasurementSessionEntity(_tmpId,_tmpMeasuredAt,_tmpScene,_tmpNote,_tmpSymptomsJson,_tmpAvgSystolic,_tmpAvgDiastolic,_tmpAvgPulse,_tmpCategory,_tmpHighRiskAlertTriggered,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<MeasurementReadingEntity> _tmpReadingsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfId);
              _tmpReadingsCollection = _collectionReadings.get(_tmpKey_1);
              _result = new MeasurementSessionWithReadings(_tmpSession,_tmpReadingsCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<MeasurementSessionWithReadings>> observeSessionsWithReadings() {
    final String _sql = "SELECT * FROM measurement_sessions ORDER BY measuredAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"measurement_readings",
        "measurement_sessions"}, new Callable<List<MeasurementSessionWithReadings>>() {
      @Override
      @NonNull
      public List<MeasurementSessionWithReadings> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfMeasuredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAt");
            final int _cursorIndexOfScene = CursorUtil.getColumnIndexOrThrow(_cursor, "scene");
            final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
            final int _cursorIndexOfSymptomsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomsJson");
            final int _cursorIndexOfAvgSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgSystolic");
            final int _cursorIndexOfAvgDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgDiastolic");
            final int _cursorIndexOfAvgPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "avgPulse");
            final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
            final int _cursorIndexOfHighRiskAlertTriggered = CursorUtil.getColumnIndexOrThrow(_cursor, "highRiskAlertTriggered");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<MeasurementReadingEntity>> _collectionReadings = new ArrayMap<String, ArrayList<MeasurementReadingEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfId);
              if (!_collectionReadings.containsKey(_tmpKey)) {
                _collectionReadings.put(_tmpKey, new ArrayList<MeasurementReadingEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(_collectionReadings);
            final List<MeasurementSessionWithReadings> _result = new ArrayList<MeasurementSessionWithReadings>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final MeasurementSessionWithReadings _item;
              final MeasurementSessionEntity _tmpSession;
              final String _tmpId;
              _tmpId = _cursor.getString(_cursorIndexOfId);
              final long _tmpMeasuredAt;
              _tmpMeasuredAt = _cursor.getLong(_cursorIndexOfMeasuredAt);
              final String _tmpScene;
              _tmpScene = _cursor.getString(_cursorIndexOfScene);
              final String _tmpNote;
              if (_cursor.isNull(_cursorIndexOfNote)) {
                _tmpNote = null;
              } else {
                _tmpNote = _cursor.getString(_cursorIndexOfNote);
              }
              final String _tmpSymptomsJson;
              if (_cursor.isNull(_cursorIndexOfSymptomsJson)) {
                _tmpSymptomsJson = null;
              } else {
                _tmpSymptomsJson = _cursor.getString(_cursorIndexOfSymptomsJson);
              }
              final int _tmpAvgSystolic;
              _tmpAvgSystolic = _cursor.getInt(_cursorIndexOfAvgSystolic);
              final int _tmpAvgDiastolic;
              _tmpAvgDiastolic = _cursor.getInt(_cursorIndexOfAvgDiastolic);
              final Integer _tmpAvgPulse;
              if (_cursor.isNull(_cursorIndexOfAvgPulse)) {
                _tmpAvgPulse = null;
              } else {
                _tmpAvgPulse = _cursor.getInt(_cursorIndexOfAvgPulse);
              }
              final String _tmpCategory;
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
              final boolean _tmpHighRiskAlertTriggered;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfHighRiskAlertTriggered);
              _tmpHighRiskAlertTriggered = _tmp != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpSession = new MeasurementSessionEntity(_tmpId,_tmpMeasuredAt,_tmpScene,_tmpNote,_tmpSymptomsJson,_tmpAvgSystolic,_tmpAvgDiastolic,_tmpAvgPulse,_tmpCategory,_tmpHighRiskAlertTriggered,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<MeasurementReadingEntity> _tmpReadingsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfId);
              _tmpReadingsCollection = _collectionReadings.get(_tmpKey_1);
              _item = new MeasurementSessionWithReadings(_tmpSession,_tmpReadingsCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllSessionsWithReadings(
      final Continuation<? super List<MeasurementSessionWithReadings>> $completion) {
    final String _sql = "SELECT * FROM measurement_sessions ORDER BY measuredAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, true, _cancellationSignal, new Callable<List<MeasurementSessionWithReadings>>() {
      @Override
      @NonNull
      public List<MeasurementSessionWithReadings> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfMeasuredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredAt");
            final int _cursorIndexOfScene = CursorUtil.getColumnIndexOrThrow(_cursor, "scene");
            final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
            final int _cursorIndexOfSymptomsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomsJson");
            final int _cursorIndexOfAvgSystolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgSystolic");
            final int _cursorIndexOfAvgDiastolic = CursorUtil.getColumnIndexOrThrow(_cursor, "avgDiastolic");
            final int _cursorIndexOfAvgPulse = CursorUtil.getColumnIndexOrThrow(_cursor, "avgPulse");
            final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
            final int _cursorIndexOfHighRiskAlertTriggered = CursorUtil.getColumnIndexOrThrow(_cursor, "highRiskAlertTriggered");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<MeasurementReadingEntity>> _collectionReadings = new ArrayMap<String, ArrayList<MeasurementReadingEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfId);
              if (!_collectionReadings.containsKey(_tmpKey)) {
                _collectionReadings.put(_tmpKey, new ArrayList<MeasurementReadingEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(_collectionReadings);
            final List<MeasurementSessionWithReadings> _result = new ArrayList<MeasurementSessionWithReadings>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final MeasurementSessionWithReadings _item;
              final MeasurementSessionEntity _tmpSession;
              final String _tmpId;
              _tmpId = _cursor.getString(_cursorIndexOfId);
              final long _tmpMeasuredAt;
              _tmpMeasuredAt = _cursor.getLong(_cursorIndexOfMeasuredAt);
              final String _tmpScene;
              _tmpScene = _cursor.getString(_cursorIndexOfScene);
              final String _tmpNote;
              if (_cursor.isNull(_cursorIndexOfNote)) {
                _tmpNote = null;
              } else {
                _tmpNote = _cursor.getString(_cursorIndexOfNote);
              }
              final String _tmpSymptomsJson;
              if (_cursor.isNull(_cursorIndexOfSymptomsJson)) {
                _tmpSymptomsJson = null;
              } else {
                _tmpSymptomsJson = _cursor.getString(_cursorIndexOfSymptomsJson);
              }
              final int _tmpAvgSystolic;
              _tmpAvgSystolic = _cursor.getInt(_cursorIndexOfAvgSystolic);
              final int _tmpAvgDiastolic;
              _tmpAvgDiastolic = _cursor.getInt(_cursorIndexOfAvgDiastolic);
              final Integer _tmpAvgPulse;
              if (_cursor.isNull(_cursorIndexOfAvgPulse)) {
                _tmpAvgPulse = null;
              } else {
                _tmpAvgPulse = _cursor.getInt(_cursorIndexOfAvgPulse);
              }
              final String _tmpCategory;
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
              final boolean _tmpHighRiskAlertTriggered;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfHighRiskAlertTriggered);
              _tmpHighRiskAlertTriggered = _tmp != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpSession = new MeasurementSessionEntity(_tmpId,_tmpMeasuredAt,_tmpScene,_tmpNote,_tmpSymptomsJson,_tmpAvgSystolic,_tmpAvgDiastolic,_tmpAvgPulse,_tmpCategory,_tmpHighRiskAlertTriggered,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<MeasurementReadingEntity> _tmpReadingsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfId);
              _tmpReadingsCollection = _collectionReadings.get(_tmpKey_1);
              _item = new MeasurementSessionWithReadings(_tmpSession,_tmpReadingsCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
            _statement.release();
          }
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object countSessions(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM measurement_sessions";
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
  public Object countReadings(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM measurement_readings";
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private void __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(
      @NonNull final ArrayMap<String, ArrayList<MeasurementReadingEntity>> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, true, (map) -> {
        __fetchRelationshipmeasurementReadingsAscomExampleBloodpressurerecordDataDbEntityMeasurementReadingEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `id`,`sessionId`,`orderIndex`,`systolic`,`diastolic`,`pulse` FROM `measurement_readings` WHERE `sessionId` IN (");
    final int _inputSize = __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : __mapKeySet) {
      _stmt.bindString(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "sessionId");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfSessionId = 1;
      final int _cursorIndexOfOrderIndex = 2;
      final int _cursorIndexOfSystolic = 3;
      final int _cursorIndexOfDiastolic = 4;
      final int _cursorIndexOfPulse = 5;
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        _tmpKey = _cursor.getString(_itemKeyIndex);
        final ArrayList<MeasurementReadingEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final MeasurementReadingEntity _item_1;
          final String _tmpId;
          _tmpId = _cursor.getString(_cursorIndexOfId);
          final String _tmpSessionId;
          _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
          final int _tmpOrderIndex;
          _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
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
          _item_1 = new MeasurementReadingEntity(_tmpId,_tmpSessionId,_tmpOrderIndex,_tmpSystolic,_tmpDiastolic,_tmpPulse);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
