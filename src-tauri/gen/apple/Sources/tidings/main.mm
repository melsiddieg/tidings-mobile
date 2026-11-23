#include "bindings/bindings.h"
#include "TidingsIos.h"

int main(int argc, char * argv[]) {
	tidings_ios_setup();
	ffi::start_app();
	return 0;
}
