package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;

public class GraphFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;
    private TextView dataText;
    
    private Connected connected = Connected.False;
    private boolean initialStart = true;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class));
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        dataText = view.findViewById(R.id.dataText);
        dataText.setMovementMethod(new ScrollingMovementMethod());
        dataText.setText("Waiting for data...\n");
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_graph, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear_graph) {
            dataText.setText("");
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void receive(ArrayDeque<byte[]> datas) {
        StringBuilder sb = new StringBuilder();
        for (byte[] data : datas) {
            // Print each byte similar to Python: print(raw)
            for (byte b : data) {
                // Display as: b'<byte_value>' format similar to Python
                sb.append("b'\\x");
                sb.append(String.format("%02x", b & 0xFF));
                sb.append("'\n");
            }
        }
        
        // Update text view on UI thread
        final String output = sb.toString();
        getActivity().runOnUiThread(() -> {
            dataText.append(output);
            
            // Auto-scroll to bottom
            final int scrollAmount = dataText.getLayout().getLineTop(dataText.getLineCount()) - dataText.getHeight();
            if (scrollAmount > 0) {
                dataText.scrollTo(0, scrollAmount);
            } else {
                dataText.scrollTo(0, 0);
            }
        });
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        Toast.makeText(getActivity(), "Connected", Toast.LENGTH_SHORT).show();
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Toast.makeText(getActivity(), "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        Toast.makeText(getActivity(), "Connection lost: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        disconnect();
    }
}
