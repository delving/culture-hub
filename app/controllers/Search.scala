package controllers

import com.escalatesoft.subcut.inject.BindingModule
import controllers.search.SearchResults

class Search(implicit val bindingModule: BindingModule) extends DelvingController with SearchResults {

  def index(query: String, page: Int) = search(query)

}