
#import <jni.h>

@interface GrowlJavaCallback : NSObject {
	jobject		_callbackObject;
	jclass		_callbackClass;
	jmethodID	_callbackMethod;
}

- (id) initWithCallback : (jobject) callback;

- (void) growlNotificationWasClicked : (NSNotification *)notification;

@end