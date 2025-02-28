import { useState, useCallback, useMemo } from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  ScrollView,
  Platform,
  Modal,
} from 'react-native';
import { Picker } from '@react-native-picker/picker';
import * as Pedometer from 'react-native-pedometer';

const TIMEZONE_OFFSETS = [
  { label: 'UTC-12', value: -12 },
  { label: 'UTC-11', value: -11 },
  { label: 'UTC-10', value: -10 },
  { label: 'UTC-9', value: -9 },
  { label: 'UTC-8 (PST)', value: -8 },
  { label: 'UTC-7', value: -7 },
  { label: 'UTC-6', value: -6 },
  { label: 'UTC-5 (EST)', value: -5 },
  { label: 'UTC-4', value: -4 },
  { label: 'UTC-3', value: -3 },
  { label: 'UTC-2', value: -2 },
  { label: 'UTC-1', value: -1 },
  { label: 'UTC+0', value: 0 },
  { label: 'UTC+1', value: 1 },
  { label: 'UTC+2', value: 2 },
  { label: 'UTC+3', value: 3 },
  { label: 'UTC+4', value: 4 },
  { label: 'UTC+5', value: 5 },
  { label: 'UTC+6', value: 6 },
  { label: 'UTC+7', value: 7 },
  { label: 'UTC+8', value: 8 },
  { label: 'UTC+9', value: 9 },
  { label: 'UTC+10', value: 10 },
  { label: 'UTC+11', value: 11 },
  { label: 'UTC+12', value: 12 },
] as const;

const App = () => {
  const [sensorAvailable, setSensorAvailable] = useState(false);
  const [permission, setPermission] = useState(false);
  const [isTracking, setIsTracking] = useState(false);
  const [stepCount, setStepCount] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [selectedTimezoneOffset, setSelectedTimezoneOffset] = useState(0);
  const [showTimezoneModal, setShowTimezoneModal] = useState(false);

  const canOperate = useMemo(
    () => sensorAvailable && permission,
    [sensorAvailable, permission]
  );

  const selectedTimezoneLabel = useMemo(() => {
    const timezone = TIMEZONE_OFFSETS.find(
      (tz) => tz.value === selectedTimezoneOffset
    );
    return timezone
      ? timezone.label
      : `UTC${selectedTimezoneOffset >= 0 ? '+' : ''}${selectedTimezoneOffset}`;
  }, [selectedTimezoneOffset]);

  const addLog = (message: string) => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs((prevLogs) => [`[${timestamp}] ${message}`, ...prevLogs]);
  };

  const handleError = useCallback((e: Error, operation: string) => {
    const errorMessage = `${operation} Error: ${e.message || JSON.stringify(e)}`;
    setError(errorMessage);
    addLog(errorMessage);
    console.error(errorMessage);
  }, []);

  const checkAvailability = useCallback(async () => {
    try {
      setError(null);
      addLog('Checking pedometer availability...');
      const isSensorAvailable = await Pedometer.isAvailable();
      setSensorAvailable(isSensorAvailable);
      addLog(`Returned availability: ${isSensorAvailable}`);
    } catch (e) {
      if (e instanceof Error) {
        handleError(e, 'Availability check');
      }
    }
  }, [handleError]);

  const checkPermission = useCallback(async () => {
    try {
      addLog('Checking pedometer permission...');
      const hasPermission = await Pedometer.requestPermission();
      setPermission(true);
      addLog(`Permission granted: ${hasPermission}`);
    } catch (e) {
      if (e instanceof Error) {
        handleError(e, 'Permission check');
      }
    }
  }, [handleError]);

  const startTracking = async () => {
    try {
      setError(null);
      addLog('Start pedometer tracking...');
      await Pedometer.startTracking();
      setIsTracking(true);
      addLog('Pedometer tracking started successfully');
    } catch (e) {
      if (e instanceof Error) {
        handleError(e, 'Tracking start');
      }
    }
  };

  const stopTracking = async () => {
    try {
      setError(null);
      addLog('Stop pedometer tracking...');
      await Pedometer.stopTracking();
      setIsTracking(false);
      addLog('Pedometer tracking stopped successfully');
    } catch (e) {
      if (e instanceof Error) {
        handleError(e, 'Tracking stop');
      }
    }
  };

  const getStartOfDay = (utcOffset: number) => {
    const now = new Date();

    const year = now.getUTCFullYear();
    const month = now.getUTCMonth();
    const date = now.getUTCDate();

    const utcMidnight = Date.UTC(year, month, date);
    const offsetMillis = utcOffset * 60 * 60 * 1000;

    return new Date(utcMidnight - offsetMillis);
  };
  const getEndOfDay = (utcOffset: number) => {
    const start = getStartOfDay(utcOffset);
    return new Date(start.getTime() + 24 * 60 * 60 * 1000 - 1);
  };

  const queryTodaySteps = async () => {
    try {
      setError(null);
      setStepCount(0);

      const startOfDay = getStartOfDay(selectedTimezoneOffset);
      const endOfDay = getEndOfDay(selectedTimezoneOffset);

      const tzDisplay = `UTC${selectedTimezoneOffset >= 0 ? '+' : ''}${selectedTimezoneOffset}`;

      addLog(
        `Querying step count from ${startOfDay.toISOString()} to ${endOfDay.toISOString()} [${tzDisplay}]`
      );

      const steps = await Pedometer.queryCount(
        startOfDay.getTime(),
        endOfDay.getTime()
      );
      setStepCount(steps);
      addLog(`Returned step count: ${steps}`);
    } catch (e) {
      if (e instanceof Error) {
        handleError(e, 'Step count query');
      }
    }
  };

  const renderTimezoneSelector = () => {
    return (
      <View style={styles.timezoneContainer}>
        <Text style={styles.timezoneLabel}>Timezone:</Text>

        <TouchableOpacity
          style={styles.timezoneButton}
          onPress={() => setShowTimezoneModal(true)}
        >
          <Text style={styles.timezoneButtonText}>{selectedTimezoneLabel}</Text>
        </TouchableOpacity>

        <Modal
          visible={showTimezoneModal}
          transparent={true}
          animationType="slide"
          onRequestClose={() => setShowTimezoneModal(false)}
        >
          <View style={styles.modalContainer}>
            <View style={styles.modalContent}>
              <Text style={styles.modalTitle}>Select Timezone Offset</Text>

              <View style={styles.pickerContainer}>
                <Picker
                  selectedValue={selectedTimezoneOffset}
                  onValueChange={(itemValue) =>
                    setSelectedTimezoneOffset(itemValue)
                  }
                  style={styles.picker}
                >
                  {TIMEZONE_OFFSETS.map((tz) => (
                    <Picker.Item
                      key={tz.value}
                      label={tz.label}
                      value={tz.value}
                    />
                  ))}
                </Picker>
              </View>

              <View style={styles.modalButtons}>
                <TouchableOpacity
                  style={[styles.modalButton, styles.cancelButton]}
                  onPress={() => setShowTimezoneModal(false)}
                >
                  <Text style={styles.cancelButtonText}>Cancel</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.modalButton, styles.confirmButton]}
                  onPress={() => setShowTimezoneModal(false)}
                >
                  <Text style={styles.confirmButtonText}>Select</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>
      </View>
    );
  };

  const renderStatus = () => {
    return (
      <View style={styles.statusContainer}>
        <Text style={styles.statusTitle}>Pedometer Status</Text>
        <Text style={styles.statusText}>
          {sensorAvailable ? 'SensorAvailable: Yes' : 'SensorAvailable: No'}
        </Text>
        <Text style={styles.statusText}>
          {permission ? 'Permission: Granted' : 'Permission: Not Granted'}
        </Text>
        <Text style={styles.statusText}>
          {isTracking ? 'Tracking: Yes' : 'Tracking: No'}
        </Text>
        {
          <Text style={styles.stepCountText}>
            Steps Today: <Text style={styles.stepCountValue}>{stepCount}</Text>
          </Text>
        }
        {error && <Text style={styles.errorText}>{error}</Text>}
      </View>
    );
  };

  const renderActions = () => {
    return (
      <View style={styles.actionsContainer}>
        <TouchableOpacity style={styles.button} onPress={checkPermission}>
          <Text style={styles.buttonText}>
            {permission ? 'Permission Granted' : 'Request Permission'}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button} onPress={checkAvailability}>
          <Text style={styles.buttonText}>
            {sensorAvailable ? 'SensorAvailable' : 'Check Availability'}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, !canOperate && styles.disabledButton]}
          onPress={
            canOperate ? (isTracking ? stopTracking : startTracking) : undefined
          }
          disabled={!canOperate}
        >
          <Text style={styles.buttonText}>
            {isTracking ? 'Stop Tracking' : 'Start Tracking'}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, !canOperate && styles.disabledButton]}
          onPress={canOperate ? queryTodaySteps : undefined}
          disabled={!canOperate}
        >
          <Text style={styles.buttonText}>Query Steps Today</Text>
        </TouchableOpacity>
      </View>
    );
  };

  const renderLogs = () => {
    return (
      <View style={styles.logsContainer}>
        <Text style={styles.logsTitle}>
          Logs ({logs.length} {logs.length === 1 ? 'entry' : 'entries'})
        </Text>
        <ScrollView style={styles.logScroll}>
          {logs.map((log, index) => (
            <Text key={index} style={styles.logText}>
              {log}
            </Text>
          ))}
          {logs.length === 0 && (
            <Text style={styles.noLogsText}>
              No logs SensorAvailable. Perform some actions to see logs.
            </Text>
          )}
        </ScrollView>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.title}>Pedometer Example</Text>
      </View>

      {renderTimezoneSelector()}
      {renderStatus()}
      {renderActions()}
      {renderLogs()}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    padding: 16,
    backgroundColor: '#3498db',
    alignItems: 'center',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: 'white',
  },
  timezoneContainer: {
    margin: 16,
    marginBottom: 8,
    padding: 12,
    backgroundColor: 'white',
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
      },
      android: {
        elevation: 3,
      },
    }),
  },
  timezoneLabel: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
    marginRight: 8,
  },
  timezoneButton: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 10,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  timezoneButtonText: {
    fontSize: 16,
    color: '#333',
  },
  modalContainer: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  modalContent: {
    backgroundColor: 'white',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: -2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
      },
      android: {
        elevation: 10,
      },
    }),
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
    marginBottom: 16,
  },
  pickerContainer: {
    ...Platform.select({
      ios: {
        height: 200,
      },
      android: {
        height: 50,
        marginVertical: 20,
      },
    }),
  },
  picker: {
    ...Platform.select({
      ios: {
        height: 200,
      },
      android: {
        height: 50,
      },
    }),
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: Platform.OS === 'ios' ? 0 : 16,
  },
  modalButton: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginHorizontal: 8,
  },
  cancelButton: {
    backgroundColor: '#f5f5f5',
    borderWidth: 1,
    borderColor: '#ddd',
  },
  confirmButton: {
    backgroundColor: '#3498db',
  },
  cancelButtonText: {
    color: '#333',
    fontSize: 16,
    fontWeight: '500',
  },
  confirmButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '500',
  },
  statusContainer: {
    margin: 16,
    marginTop: 8,
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 8,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
      },
      android: {
        elevation: 3,
      },
    }),
  },
  statusTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#333',
  },
  statusText: {
    fontSize: 16,
    marginVertical: 4,
    color: '#555',
  },
  stepCountText: {
    fontSize: 16,
    marginTop: 8,
    color: '#555',
  },
  stepCountValue: {
    fontWeight: 'bold',
    color: '#3498db',
    fontSize: 18,
  },
  errorText: {
    marginTop: 8,
    color: '#e74c3c',
    fontSize: 14,
  },
  actionsContainer: {
    margin: 16,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  button: {
    backgroundColor: '#3498db',
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
    width: '48%',
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#95a5a6',
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  logsContainer: {
    flex: 1,
    margin: 16,
    backgroundColor: 'white',
    borderRadius: 8,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
      },
      android: {
        elevation: 3,
      },
    }),
  },
  logsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    margin: 16,
    color: '#333',
  },
  logScroll: {
    flex: 1,
    padding: 16,
    paddingTop: 0,
  },
  logText: {
    fontSize: 12,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    marginBottom: 4,
    color: '#555',
  },
  noLogsText: {
    fontSize: 14,
    fontStyle: 'italic',
    color: '#95a5a6',
    textAlign: 'center',
    marginTop: 20,
  },
});

export default App;
