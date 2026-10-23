// Result.scala --- Functional error handling
// License: GPL-3.0-or-later
// Ported from Result.java (docs/scala-migration-checklist.md step 1).
// Adds map/flatMap, which the Java version lacked, per the tech doc's note.
package dev.petrolal.dotfiles.domain

enum Result[+T]:
  case Ok(value: T) extends Result[T]
  case Err(error: DotfileError) extends Result[Nothing]

  def isOk: Boolean = this match
    case Result.Ok(_) => true
    case Result.Err(_) => false

  def isErr: Boolean = !isOk

  def orElse[U >: T](fallback: U): U = this match
    case Result.Ok(value) => value
    case Result.Err(_)    => fallback

  def ifOk(action: T => Unit): Result[T] =
    this match
      case Result.Ok(value) => action(value)
      case Result.Err(_)    => ()
    this

  def ifErr(action: DotfileError => Unit): Result[T] =
    this match
      case Result.Ok(_)      => ()
      case Result.Err(error) => action(error)
    this

  def map[U](f: T => U): Result[U] = this match
    case Result.Ok(value)  => Result.Ok(f(value))
    case Result.Err(error) => Result.Err(error)

  def flatMap[U](f: T => Result[U]): Result[U] = this match
    case Result.Ok(value)  => f(value)
    case Result.Err(error) => Result.Err(error)

object Result:
  def ok[T](value: T): Result[T] = Ok(value)
  def err[T](error: DotfileError): Result[T] = Err(error)
