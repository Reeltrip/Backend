package com.reeltrip.domain.place.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.service.PlanAccessService;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.map.RouteEstimate;

@Service
public class PlaceRouteAccessService {
    private final PlaceRepository places;
    private final PlanAccessService plans;
    public PlaceRouteAccessService(PlaceRepository places,PlanAccessService plans) { this.places=places; this.plans=plans; }

    @Transactional(readOnly=true)
    public RouteEstimate.Point point(Long userId,Long placeId) {
        var place=places.findById(placeId).orElseThrow(()->new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if(!place.getUser().getUserId().equals(userId) && !plans.canReadPlace(userId,placeId)) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        if(!place.getCategory().isPlace() || place.getLatitude()==null || place.getLongitude()==null) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);
        }
        double latitude=place.getLatitude().doubleValue(),longitude=place.getLongitude().doubleValue();
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);
        }
        return new RouteEstimate.Point(latitude,longitude);
    }
}
