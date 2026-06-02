package game

// AppError is the Go equivalent of the IllegalArgumentExceptions the Java
// services throw: the HTTP layer renders it as 400 {"error": message}.
type AppError struct{ Msg string }

func (e AppError) Error() string { return e.Msg }

func badReq(msg string) error    { return AppError{Msg: msg} }
func errNotFound(m string) error { return AppError{Msg: m} }
