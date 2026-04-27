package com.example.bloodpressurerecord.data.db;

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
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao;
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao_Impl;
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao;
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao_Impl;
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao;
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile BloodPressureMeasurementDao _bloodPressureMeasurementDao;

  private volatile MeasurementSessionDao _measurementSessionDao;

  private volatile UserProfileDao _userProfileDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `bp_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberName` TEXT NOT NULL, `systolic` INTEGER NOT NULL, `diastolic` INTEGER NOT NULL, `pulse` INTEGER, `measuredAtMillis` INTEGER NOT NULL, `level` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_sessions` (`id` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, `scene` TEXT NOT NULL, `note` TEXT, `symptomsJson` TEXT, `avgSystolic` INTEGER NOT NULL, `avgDiastolic` INTEGER NOT NULL, `avgPulse` INTEGER, `category` TEXT NOT NULL, `highRiskAlertTriggered` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_readings` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `systolic` INTEGER NOT NULL, `diastolic` INTEGER NOT NULL, `pulse` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `measurement_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_readings_sessionId` ON `measurement_readings` (`sessionId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_readings_sessionId_orderIndex` ON `measurement_readings` (`sessionId`, `orderIndex`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT, `age` INTEGER, `gender` TEXT, `targetSystolic` INTEGER, `targetDiastolic` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '36b9c0d387475fb197ef10e064dfc23c')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `bp_measurements`");
        db.execSQL("DROP TABLE IF EXISTS `measurement_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `measurement_readings`");
        db.execSQL("DROP TABLE IF EXISTS `user_profile`");
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
        db.execSQL("PRAGMA foreign_keys = ON");
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
        final HashMap<String, TableInfo.Column> _columnsBpMeasurements = new HashMap<String, TableInfo.Column>(7);
        _columnsBpMeasurements.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("memberName", new TableInfo.Column("memberName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("systolic", new TableInfo.Column("systolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("diastolic", new TableInfo.Column("diastolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("pulse", new TableInfo.Column("pulse", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("measuredAtMillis", new TableInfo.Column("measuredAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBpMeasurements.put("level", new TableInfo.Column("level", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBpMeasurements = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBpMeasurements = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBpMeasurements = new TableInfo("bp_measurements", _columnsBpMeasurements, _foreignKeysBpMeasurements, _indicesBpMeasurements);
        final TableInfo _existingBpMeasurements = TableInfo.read(db, "bp_measurements");
        if (!_infoBpMeasurements.equals(_existingBpMeasurements)) {
          return new RoomOpenHelper.ValidationResult(false, "bp_measurements(com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity).\n"
                  + " Expected:\n" + _infoBpMeasurements + "\n"
                  + " Found:\n" + _existingBpMeasurements);
        }
        final HashMap<String, TableInfo.Column> _columnsMeasurementSessions = new HashMap<String, TableInfo.Column>(12);
        _columnsMeasurementSessions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("measuredAt", new TableInfo.Column("measuredAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("scene", new TableInfo.Column("scene", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("note", new TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("symptomsJson", new TableInfo.Column("symptomsJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("avgSystolic", new TableInfo.Column("avgSystolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("avgDiastolic", new TableInfo.Column("avgDiastolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("avgPulse", new TableInfo.Column("avgPulse", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("highRiskAlertTriggered", new TableInfo.Column("highRiskAlertTriggered", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementSessions.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeasurementSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMeasurementSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMeasurementSessions = new TableInfo("measurement_sessions", _columnsMeasurementSessions, _foreignKeysMeasurementSessions, _indicesMeasurementSessions);
        final TableInfo _existingMeasurementSessions = TableInfo.read(db, "measurement_sessions");
        if (!_infoMeasurementSessions.equals(_existingMeasurementSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "measurement_sessions(com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity).\n"
                  + " Expected:\n" + _infoMeasurementSessions + "\n"
                  + " Found:\n" + _existingMeasurementSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsMeasurementReadings = new HashMap<String, TableInfo.Column>(6);
        _columnsMeasurementReadings.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementReadings.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementReadings.put("orderIndex", new TableInfo.Column("orderIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementReadings.put("systolic", new TableInfo.Column("systolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementReadings.put("diastolic", new TableInfo.Column("diastolic", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementReadings.put("pulse", new TableInfo.Column("pulse", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeasurementReadings = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysMeasurementReadings.add(new TableInfo.ForeignKey("measurement_sessions", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesMeasurementReadings = new HashSet<TableInfo.Index>(2);
        _indicesMeasurementReadings.add(new TableInfo.Index("index_measurement_readings_sessionId", false, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        _indicesMeasurementReadings.add(new TableInfo.Index("index_measurement_readings_sessionId_orderIndex", true, Arrays.asList("sessionId", "orderIndex"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoMeasurementReadings = new TableInfo("measurement_readings", _columnsMeasurementReadings, _foreignKeysMeasurementReadings, _indicesMeasurementReadings);
        final TableInfo _existingMeasurementReadings = TableInfo.read(db, "measurement_readings");
        if (!_infoMeasurementReadings.equals(_existingMeasurementReadings)) {
          return new RoomOpenHelper.ValidationResult(false, "measurement_readings(com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity).\n"
                  + " Expected:\n" + _infoMeasurementReadings + "\n"
                  + " Found:\n" + _existingMeasurementReadings);
        }
        final HashMap<String, TableInfo.Column> _columnsUserProfile = new HashMap<String, TableInfo.Column>(7);
        _columnsUserProfile.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("name", new TableInfo.Column("name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("age", new TableInfo.Column("age", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("gender", new TableInfo.Column("gender", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("targetSystolic", new TableInfo.Column("targetSystolic", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("targetDiastolic", new TableInfo.Column("targetDiastolic", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfile = new TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile);
        final TableInfo _existingUserProfile = TableInfo.read(db, "user_profile");
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profile(com.example.bloodpressurerecord.data.db.entity.UserProfileEntity).\n"
                  + " Expected:\n" + _infoUserProfile + "\n"
                  + " Found:\n" + _existingUserProfile);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "36b9c0d387475fb197ef10e064dfc23c", "2f44bb1afd368130ff9517e4b2821d5e");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "bp_measurements","measurement_sessions","measurement_readings","user_profile");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `bp_measurements`");
      _db.execSQL("DELETE FROM `measurement_sessions`");
      _db.execSQL("DELETE FROM `measurement_readings`");
      _db.execSQL("DELETE FROM `user_profile`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
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
    _typeConvertersMap.put(BloodPressureMeasurementDao.class, BloodPressureMeasurementDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MeasurementSessionDao.class, MeasurementSessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserProfileDao.class, UserProfileDao_Impl.getRequiredConverters());
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
  public BloodPressureMeasurementDao measurementDao() {
    if (_bloodPressureMeasurementDao != null) {
      return _bloodPressureMeasurementDao;
    } else {
      synchronized(this) {
        if(_bloodPressureMeasurementDao == null) {
          _bloodPressureMeasurementDao = new BloodPressureMeasurementDao_Impl(this);
        }
        return _bloodPressureMeasurementDao;
      }
    }
  }

  @Override
  public MeasurementSessionDao measurementSessionDao() {
    if (_measurementSessionDao != null) {
      return _measurementSessionDao;
    } else {
      synchronized(this) {
        if(_measurementSessionDao == null) {
          _measurementSessionDao = new MeasurementSessionDao_Impl(this);
        }
        return _measurementSessionDao;
      }
    }
  }

  @Override
  public UserProfileDao userProfileDao() {
    if (_userProfileDao != null) {
      return _userProfileDao;
    } else {
      synchronized(this) {
        if(_userProfileDao == null) {
          _userProfileDao = new UserProfileDao_Impl(this);
        }
        return _userProfileDao;
      }
    }
  }
}
