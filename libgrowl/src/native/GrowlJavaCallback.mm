
#import <jni.h>
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

#import "GrowlJavaCallback.h"

static JavaVM *cachedJVM = NULL;
@implementation GrowlJavaCallback

- (id) initWithCallback : (jobject) callback {
	self = [super init];
	
	_callbackObject = callback;

	return self;
}

- (void) growlNotificationWasClicked : (NSNotification *)notification {
	// get the callback id
	NSDictionary	*callbackDictionary = (NSDictionary *)[notification userInfo];
	NSString		*callbackId = (NSString *)[callbackDictionary objectForKey:@"ClickedContext"];
	const char      *nativeString;
	jstring			convertedString;

	if (cachedJVM == NULL) {
		jsize jvmCount;
		jint jvmError = JNI_GetCreatedJavaVMs(&cachedJVM, 1, &jvmCount);
		
		if (jvmError != 0) {
			// TODO panic like crazy
			NSLog(@"Error error error");
		}
	}

	if (cachedJVM != NULL) {
		JNIEnv *env = NULL;
		cachedJVM->GetEnv((void **)&env, JNI_VERSION_1_2);
		
		_callbackClass = env->GetObjectClass(_callbackObject);
		_callbackMethod = env->GetMethodID(_callbackClass, "fireCallbacks", "(Ljava/lang/String;)V");

		nativeString = [callbackId UTF8String];
		convertedString = env->NewStringUTF(nativeString);

		// fire off the callback
		env->CallVoidMethod(_callbackObject, _callbackMethod, convertedString);
	}
}

@end