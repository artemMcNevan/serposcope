package serposcope.helpers.objects;

import com.serphacker.serposcope.models.google.GoogleSearch;

public class ScanResult {
	protected GoogleSearch googleSearch;
	protected Short rank;

	public ScanResult() {

	}

	public ScanResult(GoogleSearch googleSearch, Short rank) {
		setGoogleSearch(googleSearch);
		setRank(rank);
	}

	public GoogleSearch getGoogleSearch() {
		return googleSearch;
	}

	public void setGoogleSearch(GoogleSearch googleSearch) {
		this.googleSearch = googleSearch;
	}

	public Short getRank() {
		return rank;
	}

	public void setRank(Short rank) {
		this.rank = rank;
	}
}
