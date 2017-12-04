package com.serphacker.serposcope.task.google;

import java.util.ArrayList;
import java.util.List;

import com.serphacker.serposcope.models.google.GoogleRank;

public class GoogleTaskResult {
	protected List<GoogleRank> ranks;
	public GoogleTaskResult() {
		ranks = new ArrayList<GoogleRank>(100);
	}
	public void addRank(GoogleRank rank) {
		ranks.add(rank);
	}
	
	public List<GoogleRank> getRanks(){
		return ranks;
	}
}
