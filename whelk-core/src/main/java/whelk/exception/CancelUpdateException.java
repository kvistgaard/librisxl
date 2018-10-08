package whelk.exception;

/**
 * A NOP-exeption used to cancel a PostgreSQL storeAtomicUpdate exception from within the called closure/lambda
 */
public class CancelUpdateException extends RuntimeException
{
}
