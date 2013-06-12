package com.fluxtream.connectors.withings;

import java.util.Date;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractInstantFacetVO;
import com.fluxtream.domain.GuestSettings;

public class WithingsHeartPulseMeasureFacetVO extends AbstractInstantFacetVO<WithingsHeartPulseMeasureFacet> {

	float pulse;
	
	@Override
	public void fromFacet(WithingsHeartPulseMeasureFacet facet, TimeInterval timeInterval,
			GuestSettings settings) {
		this.startMinute = toMinuteOfDay(new Date(facet.start), timeInterval.getMainTimeZone());
		pulse = facet.heartPulse;
		description = facet.heartPulse + " bpm";
	}

}
