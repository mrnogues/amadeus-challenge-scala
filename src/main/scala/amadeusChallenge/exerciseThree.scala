/**
  * Name: exerciseThree.scala - Matching searches with bookings
  * Description:
  *   For every search in the searches file, find out whether the search ended up in a booking or not (using the info
  *   in the bookings file). For instance, search and booking origin and destination should match. For the bookings
  *   file, origin and destination are the columns dep_port and arr_port, respectively. Generate a CSV file with the
  *   search data, and an additional field, containing 1 if the search ended up in a booking, and 0 otherwise.
  */
package amadeusChallenge

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, lit, monotonically_increasing_id, substring, trim}

object exerciseThree {


  /**
   * Exercise Three: Creates a searches.csv file wth an additional column named "bookings",
   * with value "1" if that searches created a booking, or "0" if it didnt.
   */
  def execute(filePath_Bookings: String, filePath_Searches:String, spark: SparkSession): Unit = {

    //Read files into datagrames
    val dfBookingsTemp = myFunctions.readFile(filePath_Bookings, spark)
    val dfSearchesTemp = myFunctions.readFile(filePath_Searches, spark)

    //Adding index column to searches
    val dfSearchesOriginal = dfSearchesTemp.withColumn("index", monotonically_increasing_id())

    //Process files - clean data, select columns to be merged.
    val dfBookingsReady = processBookings(dfBookingsTemp)
    val dfSearchesReady = processSearches(dfSearchesOriginal)

    //Merge Bookings and Searchesby booking date, flight origin and flight destination
    val dfMerged = mergeSearchesBooking(dfBookingsReady,dfSearchesReady)

    //Merge resulting merged file back to the original searches file
    val dfSearchesOriginalWithBookings = mergeOriginal(dfSearchesOriginal,dfMerged)

    //Save searches to file
    //exerciseOne.saveFile(dfSearchesOriginalWithBookings,filePath_Searches,"_with_bookings.csv", spark)

    dfSearchesOriginalWithBookings.show(100)

  }
    /**
     * Merges
     */
    def mergeOriginal (dfSearchesOriginal:DataFrame, dfMerged:DataFrame): DataFrame = {

      // Select relevant columns from the merged file
      val dfMergedRelevantColumns = dfMerged.select("index","booking")

      // Merge bookings into Original Searches, and remove column index
      val SearchesOriginalWithBookings = dfSearchesOriginal.join(
        dfMergedRelevantColumns,
        dfSearchesOriginal.col("index")===dfMergedRelevantColumns.col("index"), "left" )
        .drop("index")

      //Fill nulls in column "booking" with value 0, and remove column index
      val searchesFinal = SearchesOriginalWithBookings.na.fill(0,Seq("booking"))

      //Return riginal searches dataframe with bookings column
      searchesFinal
    }


    /**
     * left merge bookings on Serches on booking date, flight origin and flight destination
     */
    def mergeSearchesBooking (dfBookings: DataFrame, dfSearches:DataFrame): DataFrame = {

      // Merge files Bookings with Searches on the Date, flight origin and destination
      dfSearches.join(dfBookings,
        dfSearches.col("Date") === dfBookings.col("cre_date") &&
          dfSearches.col("Origin") === dfBookings.col("dep_port") &&
          dfSearches.col("Destination") === dfBookings.col("arr_port"), "left")
    }



    /**
     * Process bookings : Selects relevant columns, and cleans the column names and row values (nulls, trim).
     */
    def processBookings(dfBookingsTemp: DataFrame): DataFrame = {

      //Adding bookings column with "ones" to Bookings
      val dfBookings = dfBookingsTemp.withColumn("booking", lit(1))

      //Clean column names
      val dfBookingsColumn = myFunctions.cleanColumnNames(dfBookings)

      //Select columns to be merged
      val dfBookingsColumnSel = dfBookingsColumn.select("arr_port","dep_port", "cre_date", "booking")

      //Clean nulls
      val dfBookingsClean = myFunctions.cleanNulls(dfBookingsColumnSel)

      // Clean date on Bookings & stripping airport columns & remove duplicates
      val dfBookingsReady = dfBookingsClean
        .withColumn("cre_date", substring(col("cre_date"),1,10) )
        .withColumn("dep_port", trim(col("dep_port")))
        .withColumn("arr_port", trim(col("arr_port")))
        .distinct()

      //Return dfBookings ready to me merged
      dfBookingsReady
    }

    /**
     * Process searches: Selects relevant columns, and cleans the column names and row values (nulls, trim).
     */
    def processSearches(dfSearches: DataFrame): DataFrame = {

      //Clean column names
      val dfSearchesColumn = myFunctions.cleanColumnNames(dfSearches)

      //Selecting only the necessary columns
      val dfSearchesColumnSel = dfSearchesColumn.select("Origin","Destination", "Date", "index")

      //Removing null values
      val dfSearchesSelClean = myFunctions.cleanNulls(dfSearchesColumnSel)

      // Clean columns Origin & Destination in Searches
      val dfSearchReady = dfSearchesSelClean.withColumn("Origin", trim(col("Origin")))
        .withColumn("Destination", trim(col("Destination")))

      //Return searches ready to be merged
      dfSearchReady
    }



}
