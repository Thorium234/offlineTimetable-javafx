package com.thorium.application.port;

import java.util.List;

public interface DataRepository {

    void clearAllData();

    List<Long> generateSampleData();
}
