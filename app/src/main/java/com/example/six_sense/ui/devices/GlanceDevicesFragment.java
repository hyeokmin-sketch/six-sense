package com.example.six_sense.ui.devices;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.SettingsStore;
import com.example.six_sense.databinding.FragmentDevicesGlanceBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.Room;

/**
 * 화면 04. 기기 제어 — 한눈에 모드.
 * 목록은 켜고 끄기만, 밝기·온도 같은 세부 조절은 카드를 눌러 DeviceDetailActivity에서.
 */
public class GlanceDevicesFragment extends Fragment implements DeviceRepository.Listener, GlanceDeviceAdapter.Callbacks {

    private FragmentDevicesGlanceBinding binding;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private SettingsStore settings;
    private GlanceDeviceAdapter adapter;
    private Room room = Room.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDevicesGlanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        settings = SixSenseApp.get().settings();
        adapter = new GlanceDeviceAdapter(this);
        binding.recyclerDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDevices.setAdapter(adapter);

        binding.groupRooms.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_room_living) room = Room.LIVING;
            else if (id == R.id.chip_room_kitchen) room = Room.KITCHEN;
            else if (id == R.id.chip_room_bath) room = Room.BATH;
            else room = Room.ALL;
            refresh();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        devices.addListener(this);
        refresh();
    }

    @Override
    public void onStop() {
        devices.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDevicesChanged() {
        if (binding == null) return;
        binding.getRoot().post(this::refresh);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void refresh() {
        adapter.submit(devices.getByRoom(room));
    }

    // ---- 어댑터 콜백

    @Override
    public void onOpen(Device device) {
        startActivity(DeviceDetailActivity.intent(requireContext(), device.getId()));
    }

    @Override
    public void onPower(Device device) {
        // 욕조 물 차 있음 / 청소 중 / 커피 내리는 중이면 안내만 하고 안 끈다 (상세 화면과 같은 규칙)
        PowerControl.request(requireContext(), device, !device.isOn());
        refresh();   // 거절됐으면 스위치를 제자리로
    }

    @Override
    public void onFavorite(Device device) {
        boolean wasFavorite = settings.isFavorite(device.getId());
        boolean now = settings.toggleFavorite(device.getId());
        if (!wasFavorite && !now) {
            Toast.makeText(requireContext(), R.string.device_favorite_full, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), now ? R.string.device_favorite_added : R.string.device_favorite_removed,
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    @Override
    public boolean isFavorite(Device device) {
        return settings.isFavorite(device.getId());
    }
}
