package views {

    package object context {

      def flash = play.mvc.Scope.Flash.current()
      def params = play.mvc.Scope.Params.current()

    }

}
