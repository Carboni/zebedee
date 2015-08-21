package com.github.onsdigital.zebedee.util;

import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.Dataset;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.DownloadSection;
import com.github.onsdigital.zebedee.content.page.statistics.document.article.Article;
import com.github.onsdigital.zebedee.content.page.statistics.document.bulletin.Bulletin;
import com.github.onsdigital.zebedee.content.page.statistics.document.figure.FigureSection;
import com.github.onsdigital.zebedee.content.page.taxonomy.ProductPage;
import com.github.onsdigital.zebedee.content.page.taxonomy.TaxonomyLandingPage;
import com.github.onsdigital.zebedee.content.partial.Link;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.model.Content;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by thomasridd on 09/07/15.
 *
 * Graph utils
 *
 */
public class GraphUtils {
    Zebedee zebedee;
    Librarian librarian;

    public GraphUtils(Zebedee zebedee) {
        this.zebedee = zebedee;
        this.librarian = new Librarian(zebedee);
    }

    // Knit functionality ----------------------------------------------------------------------------------------------
    /**
     * Knit checks through links made in the data and ensures a dense mesh of references without having to hand check
     *
     * TODO: Use and test this functionality in a wider context
     *
     * @throws IOException
     */
    public void knit() throws IOException {
        librarian.catalogue(); // Builds an index to the website

        // Ensure product pages have references to their children


        // Ensure product page cross referencing


        // Same bulletin dataset<->dataset references
        checkDatasetsInTheSameStatsBulletinReferenceEachOther();

        // If anything is linked do the vice versa
        checkNondirectionalityInGraph();

        // Except links to bulletins which should be to the current version
        removeReverseRelationshipsToOutdatedStatsBulletins();
    }

    private void checkDatasetsInTheSameStatsBulletinReferenceEachOther() throws IOException {
        // For every bulletin
        for (HashMap<String, String> bulletinDict: librarian.bulletins) {
            String uri = bulletinDict.get("Uri");
            try (InputStream stream = Files.newInputStream(zebedee.launchpad.get(uri))) {
                Bulletin bulletin = ContentUtil.deserialise(stream, Bulletin.class);
                List<Link> relatedData = bulletin.getRelatedData();

                // For every pair of datasets referenced
                for(int i = 0; i < relatedData.size() - 1; i++) {
                    for (int j = i + 1; j < relatedData.size(); j++) {
                        // Ensure they reference each other
                        ensureDatasetsBidirectional(relatedData.get(i).getUri().toString(),
                                relatedData.get(j).getUri().toString());
                    }
                }
            }
        }
    }
    private void checkNondirectionalityInGraph() {

    }
    private void removeReverseRelationshipsToOutdatedStatsBulletins() {

    }
    private void ensureDatasetsBidirectional(String uri1, String uri2) {

    }
    private void ensureBulletinsBidirectional(String uri1, String uri2) {

    }
    private void ensureDatasetsToPages(String uri1, String uri2) {

    }

    /**
     * Reverses up the hierarchy
     * @param uri
     */
    public static void backwardLink(Content content, String uri) throws IOException {

        if (Files.exists(content.toPath(uri).resolve("data.json"))) {

            // Insert file into appropriate set of links
            ProductPage productPage = null;
            try(InputStream stream = Files.newInputStream(content.toPath(uri).resolve("data.json"))) {
                Page page = ContentUtil.deserialiseContent(stream);
                if (page.getType() == PageType.bulletin) {
                    productPage = productPageForPageWithURI(content, uri);
                    ensureLink(productPage.getStatsBulletins(), uri);

                } else if (page.getType() == PageType.article) {
                    productPage = productPageForPageWithURI(content,uri);
                    ensureLink(productPage.getRelatedArticles(), uri);

                }  else if (page.getType() == PageType.dataset) {
                    productPage = productPageForPageWithURI(content, uri);
                    ensureLink(productPage.getDatasets(), uri);

                } else if (page.getType() == PageType.timeseries || page.getType() == PageType.data_slice) {
                    productPage = productPageForPageWithURI(content, uri);
                    ensureLink(productPage.getItems(), uri);

                }
            }

            // Write the file
            if (productPage != null) {
                Path productPagePath = content.toPath(productPage.getUri().toString()).resolve("data.json");
                FileUtils.writeStringToFile(productPagePath.toFile(), ContentUtil.serialise(productPage));
            }

        }
    }
    public static void backwardStrip(Content content, String uri) throws IOException {
        Path path = content.toPath(uri).resolve("data.json");
        if (Files.exists(path)) {

            try(InputStream stream = Files.newInputStream(content.toPath(uri).resolve("data.json"))) {
                // Open the file
                Page page = ContentUtil.deserialiseContent(stream);
                ProductPage productPage = null;

                // Find the parent product page
                // Strip any links
                if (page.getType() == PageType.bulletin) {
                    productPage = productPageForPageWithURI(content, uri);
                    stripAnyLink(productPage.getStatsBulletins(), uri);

                } else if (page.getType() == PageType.article) {
                    productPage = productPageForPageWithURI(content, uri);
                    stripAnyLink(productPage.getRelatedArticles(), uri);

                }  else if (page.getType() == PageType.dataset) {
                    productPage = productPageForPageWithURI(content, uri);
                    stripAnyLink(productPage.getDatasets(), uri);

                } else if (page.getType() == PageType.timeseries || page.getType() == PageType.data_slice) {
                    productPage = productPageForPageWithURI(content, uri);
                    stripAnyLink(productPage.getItems(), uri);

                }

                // Write the file
                if (productPage != null) {
                    Path productPagePath = content.toPath(productPage.getUri().toString()).resolve("data.json");
                    FileUtils.writeStringToFile(productPagePath.toFile(), ContentUtil.serialise(productPage));
                }
            }
        }
    }

    /**
     * Replaces in path and subfolders through the simple mechanism of replacing uri
     *
     * @param content
     * @param fromUri
     * @param toUri
     * @throws IOException
     */
    public static void replaceLinks(Content content, final String fromUri, final String toUri) throws IOException {
        final Path path = content.toPath(fromUri).resolve("data.json");

        if (Files.exists(path)) {

            final PathMatcher matcher = new PathMatcher() {
                @Override
                public boolean matches(Path path) {
                    if (path.toString().endsWith(".json")) {
                        return true;
                    }
                    return false;
                }
            };

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (matcher.matches(file)) {
                        replaceInFile(file, fromUri, toUri);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
    static void replaceInFile(Path path, String fromUri, String toUri) throws IOException {
        Charset charset = StandardCharsets.UTF_8;

        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll(fromUri, toUri);
        Files.write(path, content.getBytes(charset));
    }

    static ProductPage productPageForPageWithURI(Content content, String uri) throws IOException {
        String current = uri.toLowerCase();
        while( !current.equalsIgnoreCase("/") ) {
            current = current.substring(0, current.lastIndexOf("/"));
            if (content.get(current + "/data.json") != null) {
                try (InputStream stream = Files.newInputStream(content.toPath(current).resolve("data.json"))) {
                    Page page = ContentUtil.deserialiseContent(stream);
                    if (page.getType() == PageType.product_page) {
                        return (ProductPage) page;
                    }
                }
            }
        }
        return null;
    }

    static String productPageURIForPageWithURI(Content content, String uri) throws IOException {
        String current = uri.toLowerCase();
        while( !current.equalsIgnoreCase("/") ) {
            current = current.substring(0, current.lastIndexOf("/"));
            if (content.get(current + "/data.json") != null) {
                try (InputStream stream = Files.newInputStream(content.toPath(current).resolve("data.json"))) {
                    Page page = ContentUtil.deserialiseContent(stream);
                    if (page.getType() == PageType.product_page) {
                        return current;
                    }
                }
            }
        }
        return null;
    }

    private static void ensureLink(List<Link> links, String uri) {
        for (Link ref: links) {
            if (ref.getUri().toString().equalsIgnoreCase(uri)) { return; }
        }
        links.add(new Link(URI.create(uri)));
    }
    private static void stripAnyLink(List<Link> links, String uri) {
        Link strip = null;
        for (Link ref: links) {
            if (ref.getUri().toString().equalsIgnoreCase(uri)) {
                strip = ref;
                break;
            }
        }
        if (strip != null) { links.remove(strip); }
    }

    // Related links ---------------------------------------------------------------------------------------------------
    /**
     * Find all links within the bulletin/article/dataset/...
     *
     * @param bulletin
     * @return
     */
    public static List<String> relatedUris(Bulletin bulletin) {
        List<String > results = new ArrayList<>();
        for (Link ref: bulletin.getRelatedBulletins()) {
            results.add(ref.getUri().toString());
        }
        for (Link ref: bulletin.getRelatedData()) {
            results.add(ref.getUri().toString());
        }
        for (FigureSection ref: bulletin.getCharts()) {
            results.add(ref.getUri().toString() + ".json");
            results.add(ref.getUri().toString() + ".png");
            results.add(ref.getUri().toString() + "-download.png");
        }
        for (FigureSection ref: bulletin.getTables()) {
            results.add(ref.getUri().toString() + ".json");
            results.add(ref.getUri().toString() + ".html");
            results.add(ref.getUri().toString() + ".xls");
        }
        return results;
    }
    public static List<String> relatedUris(Article article) {
        List<String > results = new ArrayList<>();

        if (article == null) {return results;};

        if (article.getRelatedArticles() != null) {

            for (Link ref : article.getRelatedArticles()) {
                results.add(ref.getUri().toString());
            }
        }
        if (article.getRelatedData() != null) {
            for (Link ref : article.getRelatedData()) {
                results.add(ref.getUri().toString());
            }
        }
        if (article.getCharts() != null) {
            for (FigureSection ref : article.getCharts()) {
                results.add(ref.getUri().toString() + ".json");
                results.add(ref.getUri().toString() + ".png");
                results.add(ref.getUri().toString() + "-download.png");
            }
        }
        if (article.getTables() != null) {
            for (FigureSection ref : article.getTables()) {
                results.add(ref.getUri().toString() + ".json");
                results.add(ref.getUri().toString() + ".html");
                results.add(ref.getUri().toString() + ".xls");
            }
        }
        return results;
    }
    public static List<String> relatedUris(Dataset dataset) {
        List<String > results = new ArrayList<>();
        if (dataset.getRelatedDocuments() != null) {
            for (Link ref : dataset.getRelatedDocuments()) {
                results.add(ref.getUri().toString());
            }
        }
        if (dataset.getDownloads() != null) {
            for (DownloadSection ref : dataset.getDownloads()) {
                results.add(ref.getFile());
            }
        }
        if (dataset.getRelatedDatasets() != null) {
            for (Link ref : dataset.getRelatedDatasets()) {
                results.add(ref.getUri().toString());
            }
        }
        if (dataset.getRelatedMethodology() != null) {
            for (Link ref : dataset.getRelatedMethodology()) {
                if (ref.getUri() != null) {
                    results.add(ref.getUri().toString());
                }
            }
        }
        return results;
    }
    public static List<String> relatedUris(ProductPage productPage) {
        List<String > results = new ArrayList<>();
        if (productPage.getStatsBulletins() != null) {
            for (Link ref : productPage.getStatsBulletins()) {
                if (ref != null && ref.getUri() != null) {
                    results.add(ref.getUri().toString());
                } else {
                    results.add("NULLURI");
                }
            }
        }
        if (productPage.getItems() != null) {
            for (Link ref : productPage.getItems()) {
                results.add(ref.getUri().toString());
            }
        }
        if (productPage.getDatasets() != null) {
            for (Link ref : productPage.getDatasets()) {
                results.add(ref.getUri().toString());
            }
        }
        if (productPage.getRelatedArticles() != null) {
            for (Link ref : productPage.getRelatedArticles()) {
                results.add(ref.getUri().toString());
            }
        }

        return results;
    }
    public static List<String> relatedUris(TaxonomyLandingPage landingPage) {
        List<String > results = new ArrayList<>();

        if (landingPage.getSections() != null) {
            for (Link ref : landingPage.getSections()) {
                if (ref.getUri() != null) {
                    results.add(ref.getUri().toString());
                }
            }
        }
        return results;
    }

    public static List<String> relatedUrisForPage(Page page) {
        if (page.getType() == PageType.article) {
            return relatedUris((Article) page);
        } else if (page.getType() == PageType.bulletin) {
            return relatedUris((Bulletin) page);
        } else if (page.getType() == PageType.dataset) {
            return relatedUris((Dataset) page);
        } else if (page.getType() == PageType.product_page) {
            return relatedUris((ProductPage) page);
        } else if (page.getType() == PageType.taxonomy_landing_page) {
            return relatedUris((TaxonomyLandingPage) page);
        } else {
            return new ArrayList<>();
        }
    }


    public static void main(String[] args) throws IOException {
        final Path basePath = Paths.get("/Users/Tom.Ridd/Documents/onswebsite/workingcollections/collections");

            final List<String> relations = new ArrayList<>();
            final List<String> origins = new ArrayList<>();

            final PathMatcher matcher = Librarian.dataDotJsonMatcher();

            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (matcher.matches(file)) {
                        try(InputStream stream = Files.newInputStream(file)) {
                            Page page = ContentUtil.deserialiseContent(stream);
                            List<String> relatedUrisForPage = relatedUrisForPage(page);
                            for(String relatedUri: relatedUrisForPage) {
                                relations.add(relatedUri);
                                origins.add(basePath.relativize(file).toString());
                            }
                        }

                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        List<String> outputLines = new ArrayList<>();
        for(int i = 0; i < relations.size(); i++) {
            outputLines.add(origins.get(i) + "\t" + relations.get(i));
        }
        Path outputPath = Paths.get("/Users/Tom.Ridd/Documents/onswebsite/links.tsv");
        FileUtils.writeLines(outputPath.toFile(), outputLines);

    }
}
