package com.fluxtream.connectors.fitbit;

import java.util.Calendar;
import java.util.TimeZone;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractLocalTimeTimedFacetVO;
import com.fluxtream.domain.AbstractLocalTimeFacet;
import com.fluxtream.domain.GuestSettings;
import com.fluxtream.mvc.models.DurationModel;
import org.joda.time.format.DateTimeFormatter;

public class FitbitSleepFacetVO extends AbstractLocalTimeTimedFacetVO<FitbitSleepFacet> {

	public DurationModel minutesAsleep;
	public DurationModel minutesAwake;
	public DurationModel minutesToFallAsleep;

	@Override
	public void fromFacet(FitbitSleepFacet facet, TimeInterval timeInterval, GuestSettings settings) {
        final DateTimeFormatter time = AbstractLocalTimeFacet.timeStorageFormat.withZoneUTC();
        System.out.println("start time: " + time.print(facet.start));
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(facet.end);
        this.endMinute = c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
        c.setTimeInMillis(facet.start);
        this.startMinute = c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);

		minutesAsleep = new DurationModel(facet.minutesAsleep*60);
		minutesAwake = new DurationModel(facet.minutesAwake*60);
		minutesToFallAsleep = new DurationModel(facet.minutesToFallAsleep*60);
        date = facet.date;
	}

}
