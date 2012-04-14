(ns ^{:doc "ISBN Core"
      :author "Baishampayan Ghose <b.ghose@infinitelybeta.com>"}
  isbn.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.core.cache :as cache]))

(defonce price-cache (atom (cache/ttl-cache-factory 86400000 {})))
(defonce info-cache (atom (cache/lru-cache-factory 100 {})))

;;; 9789380032825

(def ^{:doc "Map with site url patterns and HTML selectors for the price"
       :private true}
  sites
  [{:site :flipkart
    :url "http://www.flipkart.com/search.php?query=%s"
    :selector [:span#fk-mprod-our-id html/content]}
   {:site :infibeam
    :url "http://www.infibeam.com/Books/search?q=%s"
    :selector [:span#infiPrice html/text]}
   {:site :rediff
    :url "http://books.rediff.com/book/ISBN:%s"
    :selector [:font#book-pric :b html/text]}
   {:site :indiaplaza
    :url "http://www.indiaplaza.com/searchproducts.aspx?sn=books&affid=110550&q=%s"
    :selector [:div.ourPrice :span.blueFont html/text]}
   {:site :nbcindia
    :url "http://www.nbcindia.com/Search.aspx?&StoreId=1&q=%s"
    :selector [:div.fictiong-grid-content-2 html/text]}
   {:site :pustak
    :url "http://www.pustak.co.in/pustak/books/product?bookId=%s"
    :selector [:span.prod_pg_prc_font html/text]}
   {:site :coralhub
    :url "http://www.coralhub.com/SearchResults.aspx?pindex=1&cat=0&search="
    :selector [:span#ctl00_CPBody_dlSearchResult_ctl00_tblPrice html/text]}
   {:site :bookadda
    :url "http://www.bookadda.com/search/%s"
    :selector [:span.price html/text]}
   {:site :uread
    :url "http://www.uread.com/book/isbnnetin/%s"
    :selector [:label#ctl00_phBody_ProductDetail_lblourPrice :span html/text]}
   {:site :homeshop18
    :url "http://www.homeshop18.com/search:%s/categoryid:10000"
    :selector [:span#productLayoutForm:OurPrice html/text]}
   {:site :friendsofbooks
    :url "http://www.friendsofbooks.com/store/index.php?main_page=advanced_search_result&search_in_description=1&keyword=%s"
    :selector [:.listingDescription :.productSpecialPrice html/text]}
   {:site :landmark
    :url "http://www.landmarkonthenet.com/product/SearchPaging.aspx?type=0&num=0&code=%s"
    :selector [:span.current-price html/text]}
   {:site :crossword
    :url "http://www.crossword.in/books/search?q=%s"
    :selector [:span.variant-final-price html/text]}])


(def ^{:doc "Sites which can provide us with book information."
       :private true}
  info-sites
  {:flipkart {:url "http://www.flipkart.com/search.php?query=%s"
              :selector #{[:div#mprodimg-id :img]                                    ; cover
                          [[:h1 (html/attr= :itemprop "name")]]                      ; title
                          [:div.primary-info.bmargin5 :h2 :a html/text]              ;author
                          [:span.publishername html/text]                            ; publisher
                          [:div.item_desc_text.description html/text]}}})            ; description


(defn ^:private extract-price
  "Given the node which contains the price, extract the first thing that looks like a number from it."
  [price-node]
  (when-let [price (->> price-node
                        (filter string?)
                        (map #(clojure.string/replace % #"[\r\n\s]+" ""))
                        (keep #(re-find #"[,\d]+(\.\d+)?$" %)))]
    (Float/parseFloat (clojure.string/replace price #"," ""))))


(defn fetch-url
  "Fetch & parse URL."
  [url]
  (html/html-resource (java.net.URL. url)))


(defn get-price
  "Get the price given an ISBN and site."
  [isbn {:keys [site url selector]}]
  (let [url (format url isbn)
        res {:site site :isbn isbn :url url}]
    (try
      (let [price-info (assoc res :price (extract-price (html/select (fetch-url url) selector)))]
        (swap! price-cache assoc-in [isbn (:site price-info)] price-info)
        price-info)
      (catch Exception e
        (assoc res :error e)))))


;;; XXX: One limitation here is that if a site is missing from the cache then the site
;;; can potentially be fetched more than once given concurrent requests.
;;; The right way to fix this would be to store which sites are being fetched in a ref
(defn get-all-prices
  "Given an ISBN return prices as futures."
  [isbn]
  (let [missed-sites (for [s sites :when (not (contains? (get @price-cache isbn) (:site s)))] s)]
    (doseq [ms missed-sites]
      (future (get-price isbn ms)))
    ;; put the missed sites in metadata
    (vary-meta (get @price-cache isbn {}) assoc ::miss (map :site missed-sites))))


(defn get-book-info*
  "Given an ISBN get the book info."
  [isbn]
  (try
    (let [page (fetch-url (format (get-in info-sites [:flipkart :url]) isbn))
          [img title author publisher description] (html/select page (get-in info-sites [:flipkart :selector]))]
      (when (and title author)
        (zipmap [:img :title :author :publisher :description :isbn]
                [(get-in img [:attrs :src])
                 (get-in title [:attrs :title])
                 author
                 publisher
                 (apply str (html/emit* description))
                 isbn])))
    (catch Exception _)))


(defn get-book-info
  "Given an ISBN look up the cache for the info, if not cached, fetch, cache & return."
  [isbn]
  (if-let [cached-info (get @info-cache isbn)]
    cached-info
    (when-let [info (get-book-info* isbn)]
      (get (swap! info-cache assoc isbn info) isbn))))
