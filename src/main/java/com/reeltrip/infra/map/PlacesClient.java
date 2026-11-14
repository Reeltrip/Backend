package com.reeltrip.infra.map;

import java.io.IOException;
import java.util.List;

public interface PlacesClient {
    List<MapsPlace> search(String query) throws IOException;
}
