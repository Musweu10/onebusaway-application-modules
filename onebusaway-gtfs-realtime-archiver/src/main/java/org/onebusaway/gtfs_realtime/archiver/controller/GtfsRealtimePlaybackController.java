/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_realtime.archiver.controller;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.onebusaway.gtfs_realtime.archiver.service.GtfsRealtimeRetriever;
import org.onebusaway.gtfs_realtime.archiver.service.GtfsRealtimeRetriever.EntityType;
import org.onebusaway.gtfs_realtime.archiver.service.TimeService;
import org.onebusaway.users.services.ApiKeyPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

@Controller
public class GtfsRealtimePlaybackController {

  @Autowired
  GtfsRealtimeRetriever _gtfsRealtimeRetriever;

  @Autowired
  TimeService _timeService;
  
  @Autowired
  ApiKeyPermissionService _keyService;
  
  @RequestMapping(value = "/gtfs-realtime/{path:trip-updates|vehicle-positions}")
  public void tripUpdates(ServletRequest request, HttpServletResponse response,
      @RequestParam(value = "key", required = true) String key,
      @RequestParam(value = "time", required = true) long end,
      @RequestParam(value = "interval", required = false, defaultValue = "30") long interval,
      @PathVariable String path)
          throws IOException {
    
    if(!isAllowed(key)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    
    EntityType type  = path.equals("trip-updates") ? EntityType.TRIP : EntityType.VEHICLE;

    checkTimeService(key, new Date(end * 1000));
    
    Date endDate = _timeService.getCurrentTime(key);
    Date startDate = new Date((endDate.getTime() - (interval * 1000))); 
        
    FeedMessage tripUpdates = _gtfsRealtimeRetriever.getFeedMessage(type, startDate, endDate);
    render(request, response, tripUpdates);
  }
  
  @RequestMapping(value = "/gtfs-realtime/clear")
  public @ResponseBody String clear(HttpServletResponse response,
      @RequestParam(value = "key", required = true) String key)
          throws IOException {
    
    if(!isAllowed(key)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return "";
    }
    
    _timeService.clear(key);
    return "SUCCESS\n";
  }

  private void render(ServletRequest request, HttpServletResponse response,
      FeedMessage message) throws IOException {
    if (request.getParameter("debug") != null) {
      response.setContentType("text/plain");
      response.getWriter().write(message.toString());
    } else {
      response.setContentType("application/x-google-protobuf");
      message.writeTo(response.getOutputStream());
    }
  }
  
  private void checkTimeService(String session, Date time) {
    if (!_timeService.isTimeSet(session, time)) {
      _timeService.setCurrentTime(session, time);
    }
  }
  
  private boolean isAllowed(String key) {
    return _keyService.getPermission(key, "api");
  }
}
