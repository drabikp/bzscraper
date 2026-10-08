package sk.drabikp.bzscraper.adapter.out.bandzone;


import java.util.List;

public interface GigProvider {
    List<GigSummary> findByBand(String bandSlug) throws BandzoneUploadException;
}
