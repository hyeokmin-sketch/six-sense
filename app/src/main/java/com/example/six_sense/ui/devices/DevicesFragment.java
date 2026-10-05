package com.example.six_sense.ui.devices;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.six_sense.R;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.databinding.FragmentDevicesBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceType;
import com.example.six_sense.model.Room;

/**
 * 화면 04. 기기 제어. 2열 그리드 + 방 필터.
 */
public class DevicesFragment extends Fragment implements DeviceRepository.Listener {

    private FragmentDevicesBinding binding;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private DeviceAdapter adapter;
    private Room room = Room.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDevicesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new DeviceAdapter(this::onDeviceTapped);
        binding.recyclerDevices.setLayoutManager(new GridLayoutManager(requireContext(), 2));
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

    private void onDeviceTapped(Device d) {
        if (d.getType() == DeviceType.FRIDGE) {
            // 냉장고 내용물 조회 화면은 2차 시안에서. 지금은 자리만
            Toast.makeText(requireContext(), "냉장고 내용물 조회는 준비 중이에요", Toast.LENGTH_SHORT).show();
            return;
        }
        devices.toggle(d.getId());
    }
}
