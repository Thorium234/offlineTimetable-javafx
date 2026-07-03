package com.thorium.application.usecase.data;

import com.thorium.application.port.DataRepository;

public class DataManagementUseCase {

    private final DataRepository dataRepository;

    public DataManagementUseCase(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    public void clearAllData() {
        dataRepository.clearAllData();
    }

    public int generateSampleData() {
        var ids = dataRepository.generateSampleData();
        return ids.size();
    }
}
