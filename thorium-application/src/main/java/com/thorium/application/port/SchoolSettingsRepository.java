package com.thorium.application.port;

import com.thorium.domain.model.SchoolSettings;

public interface SchoolSettingsRepository {

    SchoolSettings get();

    SchoolSettings save(SchoolSettings settings);
}
